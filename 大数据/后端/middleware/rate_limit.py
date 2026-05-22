"""
请求频率限制中间件
基于IP+接口的滑动窗口计数器，防止API被恶意调用
"""
from fastapi import Request, HTTPException
import time
from collections import defaultdict

# 配置：每个IP每个接口在时间窗口内的最大请求数
RATE_LIMIT_CONFIG = {
    "default": {"max_requests": 30, "window_seconds": 60},   # 默认：60秒30次
    "/api/v1/chat/send": {"max_requests": 10, "window_seconds": 60},  # 对话：60秒10次
    "/api/v1/auth/login": {"max_requests": 5, "window_seconds": 60},  # 登录：60秒5次
    "/api/v1/auth/register": {"max_requests": 10, "window_seconds": 60},  # 注册：60秒10次
}

# 记录结构：{ "ip:path": [timestamp1, timestamp2, ...] }
rate_log: dict[str, list[float]] = defaultdict(list)


async def rate_limit_middleware(request: Request, call_next):
    """频率限制中间件"""
    # 获取客户端IP
    ip = request.client.host if request.client else "unknown"
    path = request.url.path

    # 匹配限制规则
    config = RATE_LIMIT_CONFIG.get(path, RATE_LIMIT_CONFIG["default"])
    max_req = config["max_requests"]
    window = config["window_seconds"]

    # 清理过期记录
    key = f"{ip}:{path}"
    now = time.time()
    rate_log[key] = [t for t in rate_log[key] if now - t < window]

    # 检查是否超限
    if len(rate_log[key]) >= max_req:
        raise HTTPException(
            status_code=429,
            detail=f"请求过于频繁，请{window}秒后再试",
        )

    # 记录本次请求
    rate_log[key].append(now)

    # 继续处理请求
    response = await call_next(request)
    return response
