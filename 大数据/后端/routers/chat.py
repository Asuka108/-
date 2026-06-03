"""
对话管理路由
"""
from fastapi import APIRouter, HTTPException
from database import get_db
from models import ChatSendRequest, ChatReply, MessageItem, ConversationItem, ConversationCreateRequest
from services.ai_service import get_ai_response
from services.kb_service import search_knowledge_base
from prompts.system_prompt import SYSTEM_PROMPT

router = APIRouter()

# 转人工关键词
TRANSFER_KEYWORDS = ["转人工", "人工客服", "转接客服", "找人工", "人工服务"]


def _check_transfer_keywords(message: str) -> bool:
    """检查是否包含转人工关键词"""
    return any(kw in message for kw in TRANSFER_KEYWORDS)


def _create_transfer_request(db, conversation_id: int, user_id: int, reason: str):
    """创建转人工请求"""
    # 检查是否已有待处理的请求
    existing = db.execute(
        "SELECT id FROM transfer_requests WHERE conversation_id = %s AND status = 'pending'",
        (conversation_id,),
    ).fetchone()
    if not existing:
        db.execute(
            "INSERT INTO transfer_requests (conversation_id, user_id, reason) VALUES (%s, %s, %s)",
            (conversation_id, user_id, reason),
        )
        db.commit()


@router.post("/send")
def chat_send(req: ChatSendRequest):
    """发送消息，返回AI回复"""
    db = get_db()
    try:
        # 如果没有conversation_id，创建新对话
        conversation_id = req.conversation_id
        if not conversation_id:
            cursor = db.execute(
                "INSERT INTO conversations (user_id, title) VALUES (%s, %s)",
                (req.user_id, req.message[:20]),
            )
            db.commit()
            conversation_id = cursor.lastrowid

        # 保存用户消息
        db.execute(
            "INSERT INTO messages (conversation_id, role, content) VALUES (%s, 'user', %s)",
            (conversation_id, req.message),
        )
        db.commit()

        # 检查是否需要转人工
        if _check_transfer_keywords(req.message):
            _create_transfer_request(db, conversation_id, req.user_id, "用户主动请求转人工")
            return ChatReply(
                conversation_id=conversation_id,
                reply="好的，正在为您转接人工客服，请稍候...",
            )

        # 获取对话历史（最近10轮）
        history_rows = db.execute(
            "SELECT role, content FROM messages WHERE conversation_id = %s "
            "ORDER BY created_at DESC LIMIT 20",
            (conversation_id,),
        ).fetchall()
        history = [{"role": r["role"], "content": r["content"]} for r in reversed(history_rows)]

        # 检索知识库（复用同一个数据库连接）
        kb_context = search_knowledge_base(req.message, db=db)

        # 调用AI
        ai_reply = get_ai_response(
            user_message=req.message,
            conversation_history=history[:-1],  # 不含刚存入的用户消息
            knowledge_context=kb_context,
            system_prompt=SYSTEM_PROMPT,
        )

        # 检查AI回复是否表示无法处理
        unable_keywords = ["无法回答", "不确定", "建议联系", "无法确定", "抱歉，我无法"]
        if any(kw in ai_reply for kw in unable_keywords):
            _create_transfer_request(db, conversation_id, req.user_id, "AI无法处理")
            ai_reply += '\n\n如需进一步帮助，您可以回复"转人工"联系人工客服。'

        # 保存AI回复
        db.execute(
            "INSERT INTO messages (conversation_id, role, content) VALUES (%s, 'assistant', %s)",
            (conversation_id, ai_reply),
        )
        # 更新对话时间
        db.execute(
            "UPDATE conversations SET updated_at = NOW() WHERE id = %s",
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


@router.get("/conversations")
def list_conversations(user_id: int, page: int = 1, size: int = 20):
    """获取用户对话列表（分页）"""
    db = get_db()
    try:
        offset = (page - 1) * size
        rows = db.execute(
            "SELECT c.id, c.title, c.created_at, c.updated_at, "
            "(SELECT COUNT(*) FROM messages m WHERE m.conversation_id = c.id) AS msg_count "
            "FROM conversations c "
            "WHERE c.user_id = %s ORDER BY c.updated_at DESC LIMIT %s OFFSET %s",
            (user_id, size, offset),
        ).fetchall()
        return [
            {
                "id": r["id"],
                "title": r["title"],
                "msg_count": r["msg_count"],
                "created_at": str(r["created_at"]),
                "updated_at": str(r["updated_at"]),
            }
            for r in rows
        ]
    finally:
        db.close()


@router.post("/conversations")
def create_conversation(req: ConversationCreateRequest = None):
    """创建新对话"""
    db = get_db()
    try:
        user_id = req.user_id if req else 1
        title = req.title if req else "新对话"
        cursor = db.execute(
            "INSERT INTO conversations (user_id, title) VALUES (%s, %s)", (user_id, title)
        )
        db.commit()
        return {"id": cursor.lastrowid, "title": title, "conversation_id": str(cursor.lastrowid)}
    finally:
        db.close()
