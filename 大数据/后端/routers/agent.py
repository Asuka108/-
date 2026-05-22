"""
人工客服路由
"""
from fastapi import APIRouter, HTTPException, Header, Query
from database import get_db
from pydantic import BaseModel
from typing import Optional
import hashlib

router = APIRouter()


def hash_password(password: str) -> str:
    return hashlib.sha256(password.encode()).hexdigest()


class AgentLoginRequest(BaseModel):
    username: str
    password: str


@router.post("/login")
def agent_login(req: AgentLoginRequest):
    """客服登录"""
    db = get_db()
    try:
        agent = db.execute(
            "SELECT * FROM agents WHERE username = %s", (req.username,)
        ).fetchone()

        if not agent:
            raise HTTPException(status_code=404, detail="客服账号不存在")

        if hash_password(req.password) != agent["password_hash"]:
            raise HTTPException(status_code=401, detail="密码错误")

        # 更新状态为在线
        db.execute(
            "UPDATE agents SET status = 'online' WHERE id = %s", (agent["id"],)
        )
        db.commit()

        return {
            "id": agent["id"],
            "username": agent["username"],
            "nickname": agent["nickname"],
            "status": "online",
        }
    finally:
        db.close()


@router.post("/logout")
def agent_logout(agent_id: int):
    """客服登出"""
    db = get_db()
    try:
        db.execute(
            "UPDATE agents SET status = 'offline' WHERE id = %s", (agent_id,)
        )
        db.commit()
        return {"message": "已登出"}
    finally:
        db.close()


@router.get("/transfer-requests")
def list_transfer_requests(status: str = "pending"):
    """获取转人工请求列表"""
    db = get_db()
    try:
        rows = db.execute(
            "SELECT tr.id, tr.conversation_id, tr.user_id, tr.status, tr.reason, tr.created_at, "
            "u.username, u.nickname "
            "FROM transfer_requests tr "
            "JOIN users u ON tr.user_id = u.id "
            "WHERE tr.status = %s ORDER BY tr.created_at DESC",
            (status,),
        ).fetchall()
        return [
            {
                "id": r["id"],
                "conversation_id": r["conversation_id"],
                "user_id": r["user_id"],
                "username": r["username"],
                "nickname": r["nickname"],
                "status": r["status"],
                "reason": r["reason"],
                "created_at": str(r["created_at"]),
            }
            for r in rows
        ]
    finally:
        db.close()


@router.post("/transfer-requests/{request_id}/accept")
def accept_transfer(request_id: int, agent_id: int):
    """客服接受转人工请求"""
    db = get_db()
    try:
        # 检查请求是否存在
        req = db.execute(
            "SELECT * FROM transfer_requests WHERE id = %s AND status = 'pending'",
            (request_id,),
        ).fetchone()
        if not req:
            raise HTTPException(status_code=404, detail="请求不存在或已被处理")

        # 更新请求状态
        db.execute(
            "UPDATE transfer_requests SET status = 'accepted', agent_id = %s WHERE id = %s",
            (agent_id, request_id),
        )

        # 更新客服状态为忙碌
        db.execute(
            "UPDATE agents SET status = 'busy' WHERE id = %s", (agent_id,)
        )
        db.commit()

        return {
            "message": "已接受",
            "conversation_id": req["conversation_id"],
        }
    finally:
        db.close()


@router.post("/transfer-requests/{request_id}/close")
def close_transfer(request_id: int):
    """关闭转人工请求"""
    db = get_db()
    try:
        req = db.execute(
            "SELECT * FROM transfer_requests WHERE id = %s", (request_id,)
        ).fetchone()
        if not req:
            raise HTTPException(status_code=404, detail="请求不存在")

        db.execute(
            "UPDATE transfer_requests SET status = 'closed' WHERE id = %s", (request_id,)
        )

        # 如果有客服处理，恢复客服状态为在线
        if req["agent_id"]:
            db.execute(
                "UPDATE agents SET status = 'online' WHERE id = %s", (req["agent_id"],)
            )
        db.commit()

        return {"message": "已关闭"}
    finally:
        db.close()


@router.get("/conversations/{conversation_id}/history")
def get_conversation_history(conversation_id: int):
    """获取对话历史（客服查看）"""
    db = get_db()
    try:
        rows = db.execute(
            "SELECT id, role, content, created_at FROM messages "
            "WHERE conversation_id = %s ORDER BY created_at ASC",
            (conversation_id,),
        ).fetchall()
        return [
            {
                "id": r["id"],
                "role": r["role"],
                "content": r["content"],
                "created_at": str(r["created_at"]),
            }
            for r in rows
        ]
    finally:
        db.close()


@router.get("/online-users")
def get_online_users():
    """获取当前有活跃对话的用户列表"""
    db = get_db()
    try:
        rows = db.execute(
            "SELECT DISTINCT u.id, u.username, u.nickname, "
            "c.id as conversation_id, c.updated_at "
            "FROM users u "
            "JOIN conversations c ON u.id = c.user_id "
            "ORDER BY c.updated_at DESC LIMIT 50"
        ).fetchall()
        return [
            {
                "user_id": r["id"],
                "username": r["username"],
                "nickname": r["nickname"],
                "conversation_id": r["conversation_id"],
                "last_active": str(r["updated_at"]),
            }
            for r in rows
        ]
    finally:
        db.close()
