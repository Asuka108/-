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


class ConversationCreateRequest(BaseModel):
    user_id: int = 1
    title: str = "新对话"


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


# ====== 产品相关 ======
class ProductCreate(BaseModel):
    name: str = Field(min_length=1)
    model: Optional[str] = None
    category: Optional[str] = None
    description: Optional[str] = None
    price: float = Field(gt=0)
    original_price: Optional[float] = None
    image_url: Optional[str] = None
    images: Optional[str] = None  # JSON数组字符串
    stock: int = 100
    specs: Optional[str] = None  # JSON对象字符串
    features: Optional[str] = None  # JSON数组字符串
    is_on_sale: int = 1
    sort_order: int = 0


class ProductUpdate(BaseModel):
    name: Optional[str] = None
    model: Optional[str] = None
    category: Optional[str] = None
    description: Optional[str] = None
    price: Optional[float] = None
    original_price: Optional[float] = None
    image_url: Optional[str] = None
    images: Optional[str] = None
    stock: Optional[int] = None
    specs: Optional[str] = None
    features: Optional[str] = None
    is_on_sale: Optional[int] = None
    sort_order: Optional[int] = None


# ====== 购物车相关 ======
class CartAddRequest(BaseModel):
    product_id: int
    quantity: int = 1
    user_id: int = 1


class CartUpdateRequest(BaseModel):
    quantity: int = Field(ge=0)


# ====== 订单相关 ======
class OrderCreateRequest(BaseModel):
    receiver_name: str = Field(min_length=1)
    receiver_phone: str = Field(min_length=1)
    receiver_address: str = Field(min_length=1)
    remark: Optional[str] = None
    user_id: int = 1


# ====== 管理后台相关 ======
class AdminLoginRequest(BaseModel):
    username: str = Field(min_length=1)
    password: str = Field(min_length=1)
