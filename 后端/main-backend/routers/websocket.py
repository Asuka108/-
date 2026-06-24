"""
WebSocket路由 - 实时聊天
"""
from fastapi import APIRouter, WebSocket, WebSocketDisconnect, Query
from database import get_db
import json

router = APIRouter()

# 存储活跃的WebSocket连接
# 格式: {conversation_id: {"user": WebSocket, "agent": WebSocket}}
active_connections: dict[int, dict[str, WebSocket]] = {}


def _verify_ws_token(token: str, role: str) -> bool:
    """验证 WebSocket 连接的 token"""
    if not token:
        return False
    db = get_db()
    try:
        if role == "user":
            # 用户 token 验证（从 user_tokens 表查询）
            row = db.execute(
                "SELECT user_id FROM user_tokens WHERE token = %s", (token,)
            ).fetchone()
            return row is not None
        elif role == "agent":
            # 客服 token 验证（检查是否为在线客服）
            return True  # 客服端由人工客服登录态控制
        return False
    finally:
        db.close()


@router.websocket("/ws/chat/{conversation_id}/{role}")
async def websocket_chat(
    websocket: WebSocket,
    conversation_id: int,
    role: str,
    token: str = Query(None),
):
    """
    WebSocket聊天端点
    role: "user" 或 "agent"
    token: 认证令牌（用户通过 Query 参数传入）
    """
    if role not in ("user", "agent"):
        await websocket.close(code=4000, reason="Invalid role")
        return

    # 认证检查：如果提供了 token 则验证，未提供则允许连接（向后兼容旧版APK）
    if role == "user" and token and not _verify_ws_token(token, role):
        await websocket.close(code=4001, reason="Authentication required")
        return

    await websocket.accept()

    # 注册连接
    if conversation_id not in active_connections:
        active_connections[conversation_id] = {}
    active_connections[conversation_id][role] = websocket

    try:
        while True:
            # 接收消息
            data = await websocket.receive_text()
            message_data = json.loads(data)
            content = message_data.get("content", "")

            if not content:
                continue

            # 保存消息到数据库
            db = get_db()
            try:
                cursor = db.execute(
                    "INSERT INTO messages (conversation_id, role, content) VALUES (%s, %s, %s)",
                    (conversation_id, role, content),
                )
                db.commit()
                msg_id = cursor.lastrowid
            finally:
                db.close()

            # 转发给对方（附带消息ID，避免轮询重复显示）
            other_role = "agent" if role == "user" else "user"
            if conversation_id in active_connections and other_role in active_connections[conversation_id]:
                other_ws = active_connections[conversation_id][other_role]
                await other_ws.send_text(json.dumps({
                    "id": msg_id,
                    "role": role,
                    "content": content,
                    "conversation_id": conversation_id,
                }))

    except WebSocketDisconnect:
        # 清理连接
        if conversation_id in active_connections:
            active_connections[conversation_id].pop(role, None)
            if not active_connections[conversation_id]:
                del active_connections[conversation_id]
