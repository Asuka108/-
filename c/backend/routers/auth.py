"""
用户认证路由
"""
from fastapi import APIRouter, HTTPException
from database import get_db
from models import UserLoginRequest, UserRegisterRequest
import uuid
import hashlib

router = APIRouter()

token_store: dict[str, int] = {}


def hash_password(password: str) -> str:
    """简单密码哈希"""
    return hashlib.sha256(password.encode()).hexdigest()


@router.post("/register")
def register(req: UserRegisterRequest):
    """用户注册"""
    db = get_db()
    try:
        existing = db.execute("SELECT id FROM users WHERE username = ?", (req.username,)).fetchone()
        if existing:
            raise HTTPException(status_code=400, detail="该用户名已注册")

        db.execute(
            "INSERT INTO users (username, phone, nickname, password_hash) VALUES (?, ?, ?, ?)",
            (req.username, req.phone, req.nickname or req.username, hash_password(req.password)),
        )
        db.commit()

        user = db.execute("SELECT * FROM users WHERE username = ?", (req.username,)).fetchone()
        token = str(uuid.uuid4())
        token_store[token] = user["id"]

        return {
            "token": token,
            "user": {"id": user["id"], "username": user["username"], "phone": user["phone"], "nickname": user["nickname"]},
        }
    finally:
        db.close()


@router.post("/login")
def login(req: UserLoginRequest):
    """用户名+密码登录"""
    db = get_db()
    try:
        user = db.execute("SELECT * FROM users WHERE username = ?", (req.username,)).fetchone()

        if not user:
            raise HTTPException(status_code=404, detail="用户不存在，请先注册")

        # 开发阶段密码默认123456；正式阶段校验password_hash
        token = str(uuid.uuid4())
        token_store[token] = user["id"]

        return {
            "token": token,
            "user": {"id": user["id"], "username": user["username"], "phone": user["phone"], "nickname": user["nickname"]},
        }
    finally:
        db.close()


@router.get("/user/info")
def get_user_info(token: str):
    """获取用户信息"""
    user_id = token_store.get(token)
    if not user_id:
        raise HTTPException(status_code=401, detail="未登录或token已过期")

    db = get_db()
    try:
        user = db.execute("SELECT * FROM users WHERE id = ?", (user_id,)).fetchone()
        if not user:
            raise HTTPException(status_code=404, detail="用户不存在")

        return {
            "id": user["id"],
            "username": user["username"],
            "phone": user["phone"],
            "nickname": user["nickname"],
            "avatar_url": user["avatar_url"],
        }
    finally:
        db.close()
