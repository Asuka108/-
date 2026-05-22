"""
数据模型定义
"""
from pydantic import BaseModel, Field
from typing import Optional


# ====== 用户相关 ======
class UserLoginRequest(BaseModel):
    username: str = Field(min_length=1)
    password: str = Field(min_length=1)


class UserRegisterRequest(BaseModel):
    username: str = Field(min_length=1)
    password: str = Field(min_length=1)
    phone: Optional[str] = None
    nickname: Optional[str] = None


class UserInfo(BaseModel):
    id: int
    username: str
    phone: Optional[str] = None
    nickname: Optional[str] = None
    avatar_url: Optional[str] = None


# ====== 对话相关 ======
class ChatSendRequest(BaseModel):
    message: str
    conversation_id: Optional[int] = None
    user_id: int


class ChatReply(BaseModel):
    conversation_id: int
    reply: str


class MessageItem(BaseModel):
    id: int
    role: str
    content: str
    created_at: str


class ConversationItem(BaseModel):
    id: int
    title: str
    created_at: str
    updated_at: str


# ====== 知识库相关 ======
class KnowledgeCreate(BaseModel):
    category: str
    question: str
    answer: str
    keywords: str


class KnowledgeUpdate(BaseModel):
    category: Optional[str] = None
    question: Optional[str] = None
    answer: Optional[str] = None
    keywords: Optional[str] = None
