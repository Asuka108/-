"""
菠萝蓝牙耳机售后AI聊天机器人 - FastAPI主入口
"""
from dotenv import load_dotenv
load_dotenv()

import os
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from database import init_db
from middleware.rate_limit import rate_limit_middleware

app = FastAPI(title="菠萝耳机售后机器人API", version="1.0.0")

# CORS中间件（允许APP跨域请求）
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 频率限制中间件
app.middleware("http")(rate_limit_middleware)


@app.on_event("startup")
def startup():
    """应用启动：初始化数据库 + 自动导入知识库数据"""
    init_db()
    from services.kb_service import load_kb_from_json_to_db
    load_kb_from_json_to_db()
    print("数据库已初始化，知识库数据已加载")


@app.get("/")
def root():
    return {"message": "菠萝耳机售后机器人API运行中"}


@app.get("/health")
def health_check():
    return {"status": "ok"}


# 注册路由
from routers import auth, chat, knowledge, agent, websocket, products, cart, orders, admin
app.include_router(auth.router, prefix="/api/v1/auth", tags=["用户认证"])
app.include_router(chat.router, prefix="/api/v1/chat", tags=["对话管理"])
app.include_router(knowledge.router, prefix="/api/v1/knowledge", tags=["知识库管理"])
app.include_router(agent.router, prefix="/api/v1/agent", tags=["人工客服"])
app.include_router(websocket.router, tags=["WebSocket"])
app.include_router(products.router, prefix="/api/v1/products", tags=["产品浏览"])
app.include_router(cart.router, prefix="/api/v1/cart", tags=["购物车"])
app.include_router(orders.router, prefix="/api/v1/orders", tags=["订单管理"])
app.include_router(admin.router, prefix="/api/v1/admin", tags=["管理后台"])

# 挂载静态文件（图片上传 + 管理后台页面 + APK下载）
static_dir = os.path.join(os.path.dirname(__file__), "static")
admin_dir = os.path.join(os.path.dirname(__file__), "admin")
download_dir = os.path.join(os.path.dirname(__file__), "download")
os.makedirs(os.path.join(static_dir, "uploads"), exist_ok=True)
os.makedirs(download_dir, exist_ok=True)
app.mount("/static", StaticFiles(directory=static_dir), name="static")
app.mount("/admin", StaticFiles(directory=admin_dir, html=True), name="admin")
app.mount("/download", StaticFiles(directory=download_dir, html=True), name="download")
