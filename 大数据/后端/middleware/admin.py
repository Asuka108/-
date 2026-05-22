"""
管理后台权限校验中间件
对知识库增删改接口要求管理员token
"""
from fastapi import Request, HTTPException
from database import get_db
import os

# 管理员用户名白名单（从环境变量读取，逗号分隔）
ADMIN_USERNAMES = set(
    u.strip()
    for u in os.getenv("ADMIN_USERNAMES", "").split(",")
    if u.strip()
)


def verify_admin(token: str) -> bool:
    """验证token是否为管理员（单次查询）"""
    db = get_db()
    try:
        row = db.execute(
            "SELECT u.username FROM user_tokens t "
            "JOIN users u ON t.user_id = u.id "
            "WHERE t.token = %s",
            (token,),
        ).fetchone()
        if not row:
            return False
        return row["username"] in ADMIN_USERNAMES
    finally:
        db.close()


async def admin_required(request: Request):
    """依赖注入：检查请求是否携带有效管理员token"""
    token = request.headers.get("Authorization", "").replace("Bearer ", "")
    if not token:
        raise HTTPException(status_code=401, detail="请先登录")

    if not verify_admin(token):
        raise HTTPException(status_code=403, detail="需要管理员权限")
