"""
用户认证路由
"""
from fastapi import APIRouter, HTTPException, Header, Query
from database import get_db
from models import UserLoginRequest, UserRegisterRequest
from typing import Optional
import uuid
import hashlib

router = APIRouter()


def hash_password(password: str) -> str:
    """简单密码哈希"""
    return hashlib.sha256(password.encode()).hexdigest()


def _extract_token(
    token: Optional[str] = None,
    authorization: Optional[str] = None,
) -> Optional[str]:
    """统一提取token：优先Header，其次Query"""
    if authorization and authorization.startswith("Bearer "):
        return authorization[7:]
    return token


def get_user_id_by_token(token: str) -> Optional[int]:
    """通过token查询user_id"""
    db = get_db()
    try:
        row = db.execute(
            "SELECT user_id FROM user_tokens WHERE token = %s", (token,)
        ).fetchone()
        return row["user_id"] if row else None
    finally:
        db.close()


@router.post("/register")
def register(req: UserRegisterRequest):
    """用户名+密码注册"""
    if not req.username.strip():
        raise HTTPException(status_code=422, detail="用户名不能为空")
    if not req.password.strip():
        raise HTTPException(status_code=422, detail="密码不能为空")
    db = get_db()
    try:
        existing = db.execute(
            "SELECT id FROM users WHERE username = %s", (req.username,)
        ).fetchone()
        if existing:
            raise HTTPException(status_code=400, detail="该用户名已被注册")

        nickname = req.nickname or req.username
        phone = req.phone or None
        db.execute(
            "INSERT INTO users (username, phone, nickname, password_hash) VALUES (%s, %s, %s, %s)",
            (req.username, phone, nickname, hash_password(req.password)),
        )
        db.commit()

        user = db.execute(
            "SELECT * FROM users WHERE username = %s", (req.username,)
        ).fetchone()
        token = str(uuid.uuid4())
        db.execute(
            "INSERT INTO user_tokens (token, user_id) VALUES (%s, %s)",
            (token, user["id"]),
        )
        db.commit()

        return {
            "token": token,
            "user": {
                "id": user["id"],
                "username": user["username"],
                "phone": user.get("phone"),
                "nickname": user["nickname"],
            },
        }
    finally:
        db.close()


@router.post("/login")
def login(req: UserLoginRequest):
    """用户名+密码登录"""
    db = get_db()
    try:
        user = db.execute(
            "SELECT * FROM users WHERE username = %s", (req.username,)
        ).fetchone()

        if not user:
            raise HTTPException(status_code=404, detail="用户不存在，请先注册")

        if hash_password(req.password) != user["password_hash"]:
            raise HTTPException(status_code=401, detail="密码错误")

        token = str(uuid.uuid4())
        db.execute(
            "INSERT INTO user_tokens (token, user_id) VALUES (%s, %s)",
            (token, user["id"]),
        )
        db.commit()

        return {
            "token": token,
            "user": {
                "id": user["id"],
                "username": user["username"],
                "phone": user.get("phone"),
                "nickname": user["nickname"],
            },
        }
    finally:
        db.close()


@router.post("/logout")
def logout(
    token: Optional[str] = Query(None),
    authorization: Optional[str] = Header(None),
):
    """退出登录：删除数据库中的token"""
    actual_token = _extract_token(token, authorization)
    if not actual_token:
        raise HTTPException(status_code=401, detail="未提供token")

    db = get_db()
    try:
        result = db.execute(
            "DELETE FROM user_tokens WHERE token = %s", (actual_token,)
        )
        db.commit()
        if result.rowcount == 0:
            raise HTTPException(status_code=401, detail="token无效或已过期")
        return {"message": "退出成功"}
    finally:
        db.close()


@router.get("/user/info")
def get_user_info(
    token: Optional[str] = Query(None),
    authorization: Optional[str] = Header(None),
):
    """获取用户信息（支持Authorization header或token query参数）"""
    actual_token = _extract_token(token, authorization)
    if not actual_token:
        raise HTTPException(status_code=401, detail="未提供token")

    db = get_db()
    try:
        user = db.execute(
            "SELECT u.id, u.username, u.phone, u.nickname, u.avatar_url "
            "FROM user_tokens t JOIN users u ON t.user_id = u.id "
            "WHERE t.token = %s",
            (actual_token,),
        ).fetchone()
        if not user:
            raise HTTPException(status_code=401, detail="未登录或token已过期")

        return {
            "id": user["id"],
            "username": user["username"],
            "phone": user.get("phone"),
            "nickname": user["nickname"],
            "avatar_url": user["avatar_url"],
        }
    finally:
        db.close()
