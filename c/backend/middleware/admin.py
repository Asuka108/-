"""
管理后台权限校验中间件
对知识库增删改接口要求管理员token
"""
from fastapi import Request, HTTPException
from routers.auth import token_store
from database import get_db
import os

# 管理员手机号白名单（从环境变量读取，逗号分隔）
ADMIN_PHONES = set(
    phone.strip()
    for phone in os.getenv("ADMIN_PHONES", "").split(",")
    if phone.strip()
)


def verify_admin(token: str) -> bool:
    """验证token是否为管理员"""
    user_id = token_store.get(token)
    if not user_id:
        return False

    db = get_db()
    try:
        user = db.execute("SELECT phone FROM users WHERE id = ?", (user_id,)).fetchone()
        if not user:
            return False
        return user["phone"] in ADMIN_PHONES
    finally:
        db.close()


async def admin_required(request: Request):
    """依赖注入：检查请求是否携带有效管理员token"""
    token = request.headers.get("Authorization", "").replace("Bearer ", "")
    if not token:
        raise HTTPException(status_code=401, detail="请先登录")

    if not verify_admin(token):
        raise HTTPException(status_code=403, detail="需要管理员权限")
