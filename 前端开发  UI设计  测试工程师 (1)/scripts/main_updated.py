"""
菠萝耳机售后AI客服系统 - FastAPI后端
"""
from dotenv import load_dotenv
load_dotenv()

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from database import init_db, get_db
from middleware.rate_limit import rate_limit_middleware
import json
import asyncio

app = FastAPI(title="菠萝耳机售后客服API", version="2.1.1")

# CORS中间件——允许APP访问
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 频率限制中间件
app.middleware("http")(rate_limit_middleware)


# ====== WebSocket 连接管理器 ======
class ConnectionManager:
    def __init__(self):
        self.connections: dict[str, dict] = {}  # {conv_id: {user: ws, agent: ws}}

    async def connect(self, conv_id: str, role: str, ws: WebSocket):
        await ws.accept()
        if conv_id not in self.connections:
            self.connections[conv_id] = {}
        self.connections[conv_id][role] = ws
        # 通知对方已连接
        other = "agent" if role == "user" else "user"
        if other in self.connections[conv_id]:
            try:
                await self.connections[conv_id][other].send_text(json.dumps({
                    "type": "partner_connected",
                    "role": role,
                    "content": "对方已连接" if role != "agent" else "客服已接入，请直接输入您的问题"
                }))
            except Exception:
                pass
        # 如果agent接入，通知user
        if role == "agent":
            try:
                await ws.send_text(json.dumps({
                    "type": "agent_connected",
                    "content": "客服已接入，请直接输入您的问题"
                }))
            except Exception:
                pass

    def disconnect(self, conv_id: str, role: str):
        if conv_id in self.connections and role in self.connections[conv_id]:
            del self.connections[conv_id][role]
            if not self.connections[conv_id]:
                del self.connections[conv_id]

    async def send_to_other(self, conv_id: str, sender_role: str, message: str):
        if conv_id not in self.connections:
            return
        other = "agent" if sender_role == "user" else "user"
        if other in self.connections[conv_id]:
            try:
                await self.connections[conv_id][other].send_text(json.dumps({
                    "role": sender_role,
                    "content": message
                }))
            except Exception:
                self.disconnect(conv_id, other)

    async def send_to_role(self, conv_id: str, role: str, message: dict):
        if conv_id in self.connections and role in self.connections[conv_id]:
            try:
                await self.connections[conv_id][role].send_text(json.dumps(message))
            except Exception:
                self.disconnect(conv_id, role)


manager = ConnectionManager()


@app.websocket("/ws/chat/{conv_id}/user")
async def ws_user(websocket: WebSocket, conv_id: str):
    """用户端WebSocket"""
    await manager.connect(conv_id, "user", websocket)
    try:
        while True:
            data = await websocket.receive_text()
            msg = json.loads(data)
            content = msg.get("content", "")

            # 保存消息到数据库
            db = get_db()
            try:
                db.execute(
                    "INSERT INTO messages (conversation_id, role, source, content) VALUES (?, 'user', 'user', ?)",
                    (int(conv_id), content),
                )
                db.commit()
            finally:
                db.close()

            # 转发给agent
            await manager.send_to_other(conv_id, "user", content)
    except (WebSocketDisconnect, Exception):
        manager.disconnect(conv_id, "user")


@app.websocket("/ws/chat/{conv_id}/agent")
async def ws_agent(websocket: WebSocket, conv_id: str):
    """客服端WebSocket"""
    await manager.connect(conv_id, "agent", websocket)
    try:
        while True:
            data = await websocket.receive_text()
            msg = json.loads(data)
            content = msg.get("content", "")

            # 保存客服消息到数据库
            db = get_db()
            try:
                db.execute(
                    "INSERT INTO messages (conversation_id, role, source, content) VALUES (?, 'agent', 'human', ?)",
                    (int(conv_id), content),
                )
                db.commit()
            finally:
                db.close()

            # 转发给用户
            await manager.send_to_other(conv_id, "agent", content)
    except (WebSocketDisconnect, Exception):
        manager.disconnect(conv_id, "agent")


# ====== 服务生命周期 ======
@app.on_event("startup")
def startup():
    """应用启动：初始化数据库 + 自动导入知识库数据"""
    init_db()
    from services.kb_service import load_kb_from_json_to_db
    load_kb_from_json_to_db()
    print("数据库已初始化，知识库数据已加载")


@app.get("/")
def root():
    return {"message": "菠萝耳机售后客服API服务"}


@app.get("/health")
def health_check():
    return {"status": "ok"}


# 注册路由
from routers import auth, chat, knowledge, agent
app.include_router(auth.router, prefix="/api/v1/auth", tags=["用户认证"])
app.include_router(chat.router, prefix="/api/v1/chat", tags=["对话服务"])
app.include_router(knowledge.router, prefix="/api/v1/knowledge", tags=["知识库管理"])
app.include_router(agent.router, prefix="/api/v1/agent", tags=["人工客服"])
