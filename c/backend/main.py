"""
菠萝蓝牙耳机售后AI聊天机器人 - FastAPI主入口
"""
from dotenv import load_dotenv
load_dotenv()  # 必须在import database之前加载环境变量

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
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
from routers import auth, chat, knowledge
app.include_router(auth.router, prefix="/api/v1/auth", tags=["用户认证"])
app.include_router(chat.router, prefix="/api/v1/chat", tags=["对话管理"])
app.include_router(knowledge.router, prefix="/api/v1/knowledge", tags=["知识库管理"])
