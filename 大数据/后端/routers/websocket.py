"""
WebSocket路由 - 实时聊天
"""
from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from database import get_db
import json

router = APIRouter()

# 存储活跃的WebSocket连接
# 格式: {conversation_id: {"user": WebSocket, "agent": WebSocket}}
active_connections: dict[int, dict[str, WebSocket]] = {}


@router.websocket("/ws/chat/{conversation_id}/{role}")
async def websocket_chat(websocket: WebSocket, conversation_id: int, role: str):
    """
    WebSocket聊天端点
    role: "user" 或 "agent"
    """
    if role not in ("user", "agent"):
        await websocket.close(code=4000, reason="Invalid role")
        return

    await websocket.accept()

    # 注册连接
    if conversation_id not in active_connections:
        active_connections[conversation_id] = {}
    active_connections[conversation_id][role] = websocket

    try:
        while True:
            # 接收消息
            data = await websocket.receive_text()
            message_data = json.loads(data)
            content = message_data.get("content", "")

            if not content:
                continue

            # 保存消息到数据库
            db = get_db()
            try:
                db.execute(
                    "INSERT INTO messages (conversation_id, role, content) VALUES (%s, %s, %s)",
                    (conversation_id, role, content),
                )
                db.commit()
            finally:
                db.close()

            # 转发给对方
            other_role = "agent" if role == "user" else "user"
            if conversation_id in active_connections and other_role in active_connections[conversation_id]:
                other_ws = active_connections[conversation_id][other_role]
                await other_ws.send_text(json.dumps({
                    "role": role,
                    "content": content,
                    "conversation_id": conversation_id,
                }))

    except WebSocketDisconnect:
        # 清理连接
        if conversation_id in active_connections:
            active_connections[conversation_id].pop(role, None)
            if not active_connections[conversation_id]:
                del active_connections[conversation_id]
