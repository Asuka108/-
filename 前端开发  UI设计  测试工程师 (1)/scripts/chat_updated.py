"""
对话服务路由
"""
from fastapi import APIRouter, HTTPException
from database import get_db
from models import ChatSendRequest, ChatReply, MessageItem, ConversationItem
from services.ai_service import get_ai_response
from services.kb_service import search_knowledge_base
from prompts.system_prompt import SYSTEM_PROMPT

router = APIRouter()

# 转人工关键词
TRANSFER_KEYWORDS = ["转人工", "人工客服", "人工", "转人工客服", "人工服务"]


@router.post("/send")
def chat_send(req: ChatSendRequest):
    """发送消息，调用AI回复"""
    db = get_db()
    try:
        # 没有conversation_id则创建新对话
        conversation_id = req.conversation_id
        if not conversation_id:
            cursor = db.execute(
                "INSERT INTO conversations (user_id, title) VALUES (?, ?)",
                (req.user_id, req.message[:20]),
            )
            db.commit()
            conversation_id = cursor.lastrowid

        # 转人工已接入 → 只保存消息，不调AI（客服通过WebSocket实时收到）
        transfer = db.execute(
            "SELECT id FROM transfer_requests "
            "WHERE conversation_id = ? AND status = 'accepted' LIMIT 1",
            (conversation_id,),
        ).fetchone()
        if transfer:
            db.execute(
                "INSERT INTO messages (conversation_id, role, source, content) VALUES (?, 'user', 'user', ?)",
                (conversation_id, req.message),
            )
            db.execute(
                "UPDATE conversations SET updated_at = NOW() WHERE id = ?",
                (conversation_id,),
            )
            db.commit()
            return ChatReply(conversation_id=conversation_id, reply="")

        # 检测是否转人工请求
        is_transfer = any(kw in req.message for kw in TRANSFER_KEYWORDS)

        if is_transfer:
            # 保存用户消息
            db.execute(
                "INSERT INTO messages (conversation_id, role, source, content) VALUES (?, 'user', 'user', ?)",
                (conversation_id, req.message),
            )
            db.commit()

            # 创建转人工请求（去重：同一会话已有pending/accepted则不重复创建）
            existing = db.execute(
                "SELECT id FROM transfer_requests WHERE conversation_id = ? AND status IN ('pending','accepted')",
                (conversation_id,),
            ).fetchone()

            if not existing:
                db.execute(
                    "INSERT INTO transfer_requests (conversation_id, user_id, status, reason) VALUES (?, ?, 'pending', ?)",
                    (conversation_id, req.user_id, req.message),
                )
                db.commit()

            # 保存系统提示消息
            reply_text = "已提交转人工请求，请耐心等待客服接入。您可以继续描述您的问题，客服接入后会看到之前的聊天记录。"
            db.execute(
                "INSERT INTO messages (conversation_id, role, source, content) VALUES (?, 'assistant', 'ai', ?)",
                (conversation_id, reply_text),
            )
            db.execute(
                "UPDATE conversations SET updated_at = NOW() WHERE id = ?",
                (conversation_id,),
            )
            db.commit()
            return ChatReply(conversation_id=conversation_id, reply=reply_text)

        # 保存用户消息
        db.execute(
            "INSERT INTO messages (conversation_id, role, source, content) VALUES (?, 'user', 'user', ?)",
            (conversation_id, req.message),
        )
        db.commit()

        # 获取对话历史（最近10轮）
        history_rows = db.execute(
            "SELECT role, content FROM messages WHERE conversation_id = ? "
            "ORDER BY created_at DESC LIMIT 20",
            (conversation_id,),
        ).fetchall()
        history = [{"role": r["role"], "content": r["content"]} for r in reversed(history_rows)]

        # 检索知识库
        kb_context = search_knowledge_base(req.message)

        # 调用AI
        ai_reply = get_ai_response(
            user_message=req.message,
            conversation_history=history[:-1],  # 排除刚存的用户消息
            knowledge_context=kb_context,
            system_prompt=SYSTEM_PROMPT,
        )

        # 保存AI回复
        db.execute(
            "INSERT INTO messages (conversation_id, role, source, content) VALUES (?, 'assistant', 'ai', ?)",
            (conversation_id, ai_reply),
        )
        # 更新对话时间
        db.execute(
            "UPDATE conversations SET updated_at = NOW() WHERE id = ?",
            (conversation_id,),
        )
        db.commit()

        return ChatReply(conversation_id=conversation_id, reply=ai_reply)
    finally:
        db.close()


@router.get("/history/{conversation_id}")
def get_history(conversation_id: int):
    """获取对话历史消息"""
    db = get_db()
    try:
        rows = db.execute(
            "SELECT id, role, content, created_at FROM messages "
            "WHERE conversation_id = ? ORDER BY created_at ASC",
            (conversation_id,),
        ).fetchall()
        return [
            {
                "id": r["id"],
                "role": r["role"],
                "content": r["content"],
                "created_at": r["created_at"],
            }
            for r in rows
        ]
    finally:
        db.close()


@router.get("/conversations")
def list_conversations(user_id: int, page: int = 1, size: int = 20):
    """获取用户对话列表（分页）"""
    db = get_db()
    try:
        offset = (page - 1) * size
        rows = db.execute(
            "SELECT id, title, created_at, updated_at FROM conversations "
            "WHERE user_id = ? ORDER BY updated_at DESC LIMIT ? OFFSET ?",
            (user_id, size, offset),
        ).fetchall()
        return [
            {
                "id": r["id"],
                "title": r["title"],
                "created_at": r["created_at"],
                "updated_at": r["updated_at"],
            }
            for r in rows
        ]
    finally:
        db.close()


@router.post("/conversations")
def create_conversation(user_id: int, title: str = "新对话"):
    """创建新对话"""
    db = get_db()
    try:
        cursor = db.execute(
            "INSERT INTO conversations (user_id, title) VALUES (?, ?)", (user_id, title)
        )
        db.commit()
        return {"id": cursor.lastrowid, "title": title}
    finally:
        db.close()


@router.get("/transfer-requests/status")
def check_transfer_status(conversation_id: int):
    """查询某对话的转人工状态"""
    db = get_db()
    try:
        req = db.execute(
            "SELECT id, status, agent_id, created_at FROM transfer_requests "
            "WHERE conversation_id = ? ORDER BY created_at DESC LIMIT 1",
            (conversation_id,),
        ).fetchone()
        if not req:
            return {"status": "none"}
        return {
            "id": req["id"],
            "status": req["status"],
            "agent_id": req["agent_id"],
            "created_at": str(req["created_at"]),
        }
    finally:
        db.close()
