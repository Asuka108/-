import paramiko

client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect('8.137.205.18', username='root', password='Dsj123456.', timeout=15)

sftp = client.open_sftp()

# Read local chat.py content from embedded string
chat_content = """\"\"\"
对话管理路由
\"\"\"
from fastapi import APIRouter, HTTPException
from database import get_db
from models import ChatSendRequest, ChatReply, MessageItem, ConversationItem
from services.ai_service import get_ai_response
from services.kb_service import search_knowledge_base
from prompts.system_prompt import SYSTEM_PROMPT

router = APIRouter()

TRANSFER_KEYWORDS = ["转人工", "人工客服", "人工服务", "客服", "转人工服务", "人工", "human"]


def _check_transfer_keywords(message: str) -> bool:
    return any(kw in message for kw in TRANSFER_KEYWORDS)


def _create_transfer_request(db, conversation_id: int, user_id: int, reason: str):
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


def _is_transfer_active(db, conversation_id: int) -> bool:
    req = db.execute(
        "SELECT id FROM transfer_requests WHERE conversation_id = %s AND status = 'accepted'",
        (conversation_id,),
    ).fetchone()
    return req is not None


@router.post("/send")
def chat_send(req: ChatSendRequest):
    db = get_db()
    try:
        conversation_id = req.conversation_id
        if not conversation_id:
            cursor = db.execute(
                "INSERT INTO conversations (user_id, title) VALUES (%s, %s)",
                (req.user_id, req.message[:20]),
            )
            db.commit()
            conversation_id = cursor.lastrowid

        db.execute(
            "INSERT INTO messages (conversation_id, role, source, content) VALUES (%s, 'user', 'user', %s)",
            (conversation_id, req.message),
        )
        db.commit()

        if _check_transfer_keywords(req.message):
            _create_transfer_request(db, conversation_id, req.user_id, "用户请求转人工客服")
            db.execute("UPDATE conversations SET updated_at = NOW() WHERE id = %s", (conversation_id,))
            db.commit()
            return ChatReply(conversation_id=conversation_id, reply="已收到您的转人工请求，请稍等，客服将尽快接入...")

        if _is_transfer_active(db, conversation_id):
            db.execute("UPDATE conversations SET updated_at = NOW() WHERE id = %s", (conversation_id,))
            db.commit()
            return ChatReply(conversation_id=conversation_id, reply="人工客服已接入，请直接输入您的问题")

        history_rows = db.execute(
            "SELECT role, content FROM messages WHERE conversation_id = %s ORDER BY created_at DESC LIMIT 20",
            (conversation_id,),
        ).fetchall()
        history = [{"role": r["role"], "content": r["content"]} for r in reversed(history_rows)]

        kb_context = search_knowledge_base(req.message)

        ai_reply = get_ai_response(
            user_message=req.message,
            conversation_history=history[:-1],
            knowledge_context=kb_context,
            system_prompt=SYSTEM_PROMPT,
        )

        db.execute(
            "INSERT INTO messages (conversation_id, role, source, content) VALUES (%s, 'assistant', 'ai', %s)",
            (conversation_id, ai_reply),
        )
        db.execute("UPDATE conversations SET updated_at = NOW() WHERE id = %s", (conversation_id,))
        db.commit()

        return ChatReply(conversation_id=conversation_id, reply=ai_reply)
    finally:
        db.close()


@router.get("/history/{conversation_id}")
def get_history(conversation_id: int):
    db = get_db()
    try:
        rows = db.execute(
            "SELECT id, role, content, created_at FROM messages WHERE conversation_id = %s ORDER BY created_at ASC",
            (conversation_id,),
        ).fetchall()
        return [{"id": r["id"], "role": r["role"], "content": r["content"], "created_at": str(r["created_at"])} for r in rows]
    finally:
        db.close()


@router.get("/conversations")
def list_conversations(user_id: int, page: int = 1, size: int = 20):
    db = get_db()
    try:
        offset = (page - 1) * size
        rows = db.execute(
            "SELECT id, title, created_at, updated_at FROM conversations WHERE user_id = %s ORDER BY updated_at DESC LIMIT %s OFFSET %s",
            (user_id, size, offset),
        ).fetchall()
        return [{"id": r["id"], "title": r["title"], "created_at": str(r["created_at"]), "updated_at": str(r["updated_at"])} for r in rows]
    finally:
        db.close()


@router.post("/conversations")
def create_conversation(user_id: int, title: str = "新对话"):
    db = get_db()
    try:
        cursor = db.execute(
            "INSERT INTO conversations (user_id, title) VALUES (%s, %s)", (user_id, title)
        )
        db.commit()
        return {"id": cursor.lastrowid, "title": title}
    finally:
        db.close()
"""

with sftp.open('/var/www/after-sales-robot/backend/routers/chat.py', 'w') as f:
    f.write(chat_content)

ws_content = """\"\"\"
WebSocket路由 - 实时聊天
\"\"\"
from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from database import get_db
import json

router = APIRouter()

active_connections = {}


@router.websocket("/ws/chat/{conversation_id}/{role}")
async def websocket_chat(websocket: WebSocket, conversation_id: int, role: str):
    if role not in ("user", "agent"):
        await websocket.close(code=4000, reason="Invalid role")
        return
    await websocket.accept()

    key = str(conversation_id)
    if key not in active_connections:
        active_connections[key] = {}
    active_connections[key][role] = websocket

    if role == "agent":
        db = get_db()
        try:
            db.execute(
                "UPDATE transfer_requests SET status = 'accepted' WHERE conversation_id = %s AND status = 'pending'",
                (conversation_id,),
            )
            db.commit()
        finally:
            db.close()

    try:
        while True:
            data = await websocket.receive_text()
            message_data = json.loads(data)
            content = message_data.get("content", "")
            if not content:
                continue

            source = "agent" if role == "agent" else "user"
            db = get_db()
            try:
                db.execute(
                    "INSERT INTO messages (conversation_id, role, source, content) VALUES (%s, %s, %s, %s)",
                    (conversation_id, role, source, content),
                )
                db.execute("UPDATE conversations SET updated_at = NOW() WHERE id = %s", (conversation_id,))
                db.commit()
            finally:
                db.close()

            other_role = "agent" if role == "user" else "user"
            if key in active_connections and other_role in active_connections[key]:
                try:
                    await active_connections[key][other_role].send_text(json.dumps({
                        "role": role, "content": content, "conversation_id": conversation_id
                    }))
                except Exception:
                    pass
    except WebSocketDisconnect:
        pass
    finally:
        if key in active_connections:
            active_connections[key].pop(role, None)
            if not active_connections[key]:
                del active_connections[key]
"""

with sftp.open('/var/www/after-sales-robot/backend/routers/websocket.py', 'w') as f:
    f.write(ws_content)

sftp.close()
print('Files written')

# Restart
stdin, stdout, stderr = client.exec_command('rm -rf /var/www/after-sales-robot/backend/__pycache__ /var/www/after-sales-robot/backend/routers/__pycache__ && systemctl restart after-sales-robot && sleep 5 && systemctl is-active after-sales-robot && echo "" && curl -s http://localhost:8000/health')
print('restart:', stdout.read().decode('utf-8', errors='ignore'))

# Test transfer
stdin2, stdout2, stderr2 = client.exec_command("""curl -s -X POST http://localhost:8000/api/v1/chat/send -H 'Content-Type: application/json' -d '{"message":"转人工","user_id":3}'""")
print('transfer:', stdin2.read().decode('utf-8', errors='ignore'))

# Check pending
stdin3, stdout3, stderr3 = client.exec_command("""curl -s 'http://localhost:8000/api/v1/agent/transfer-requests?status=pending'""")
print('pending:', stdin3.read().decode('utf-8', errors='ignore')[:300])

client.close()
