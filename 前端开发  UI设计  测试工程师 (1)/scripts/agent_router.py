"""
人工客服路由
"""
from fastapi import APIRouter, HTTPException, Query
from database import get_db
from pydantic import BaseModel
import hashlib

router = APIRouter()


class AgentLoginRequest(BaseModel):
    username: str
    password: str


def hash_password(password: str) -> str:
    return hashlib.sha256(password.encode()).hexdigest()


@router.post("/login")
def agent_login(req: AgentLoginRequest):
    db = get_db()
    try:
        agent = db.execute(
            "SELECT id, username, nickname, status FROM agents WHERE username = ? AND password_hash = ?",
            (req.username, hash_password(req.password)),
        ).fetchone()
        if not agent:
            raise HTTPException(status_code=401, detail="用户名或密码错误")

        db.execute("UPDATE agents SET status = 'online' WHERE id = ?", (agent["id"],))
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
def agent_logout(agent_id: int = Query(...)):
    db = get_db()
    try:
        db.execute("UPDATE agents SET status = 'offline' WHERE id = ?", (agent_id,))
        db.commit()
        return {"ok": True}
    finally:
        db.close()


@router.get("/transfer-requests")
def list_transfer_requests(status: str = "pending"):
    db = get_db()
    try:
        rows = db.execute(
            "SELECT tr.id, tr.conversation_id, tr.user_id, tr.reason, tr.created_at, "
            "u.username, u.nickname "
            "FROM transfer_requests tr "
            "LEFT JOIN users u ON tr.user_id = u.id "
            "WHERE tr.status = ? ORDER BY tr.created_at ASC",
            (status,),
        ).fetchall()
        return [
            {
                "id": r["id"],
                "conversation_id": r["conversation_id"],
                "user_id": r["user_id"],
                "username": r["username"],
                "nickname": r["nickname"],
                "reason": r["reason"],
                "created_at": str(r["created_at"]),
            }
            for r in rows
        ]
    finally:
        db.close()


@router.post("/transfer-requests/{req_id}/accept")
def accept_transfer_request(req_id: int, agent_id: int = Query(...)):
    db = get_db()
    try:
        req = db.execute(
            "SELECT * FROM transfer_requests WHERE id = ?", (req_id,)
        ).fetchone()
        if not req:
            raise HTTPException(status_code=404, detail="请求不存在")

        db.execute(
            "UPDATE transfer_requests SET status = 'accepted', agent_id = ?, updated_at = NOW() WHERE id = ?",
            (agent_id, req_id),
        )
        db.execute("UPDATE agents SET status = 'busy' WHERE id = ?", (agent_id,))
        db.commit()
        return {"ok": True}
    finally:
        db.close()


@router.post("/transfer-requests/by-conversation/{conv_id}/close")
def close_transfer_request(conv_id: int):
    db = get_db()
    try:
        db.execute(
            "UPDATE transfer_requests SET status = 'closed', updated_at = NOW() WHERE conversation_id = ? AND status = 'accepted'",
            (conv_id,),
        )
        db.commit()
        return {"ok": True}
    finally:
        db.close()


@router.get("/online-users")
def list_online_users():
    db = get_db()
    try:
        rows = db.execute(
            "SELECT DISTINCT c.id AS conversation_id, c.user_id, u.username, u.nickname, c.updated_at AS last_active "
            "FROM conversations c "
            "LEFT JOIN users u ON c.user_id = u.id "
            "WHERE c.updated_at > DATE_SUB(NOW(), INTERVAL 30 MINUTE) "
            "ORDER BY c.updated_at DESC LIMIT 50"
        ).fetchall()
        return [
            {
                "conversation_id": r["conversation_id"],
                "user_id": r["user_id"],
                "username": r["username"],
                "nickname": r["nickname"],
                "last_active": str(r["last_active"]),
            }
            for r in rows
        ]
    finally:
        db.close()


@router.get("/conversations/{conv_id}/history")
def get_conversation_history(conv_id: int):
    db = get_db()
    try:
        rows = db.execute(
            "SELECT id, role, content, created_at FROM messages "
            "WHERE conversation_id = ? ORDER BY created_at ASC",
            (conv_id,),
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
