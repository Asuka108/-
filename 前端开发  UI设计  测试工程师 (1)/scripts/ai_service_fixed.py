"""
AI服务 - 调用DeepSeek API获取智能回复
"""
import os
import requests
from dotenv import load_dotenv

load_dotenv(os.path.join(os.path.dirname(__file__), "..", ".env"))

DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY")
DEEPSEEK_API_URL = os.getenv("DEEPSEEK_API_URL", "https://api.deepseek.com/v1/chat/completions")


def get_ai_response(
    user_message: str,
    conversation_history: list[dict],
    knowledge_context: str,
    system_prompt: str,
) -> str:
    """
    调用DeepSeek API获取AI回复
    """
    # 构建消息列表
    messages = [{"role": "system", "content": system_prompt}]

    # 注入知识库上下文
    if knowledge_context:
        messages.append({
            "role": "system",
            "content": f"请参考知识库数据，优先据此回答：\n{knowledge_context}",
        })

    # 注入关键词预检：判断用户是否问了耳机相关
    headphone_keywords = [
        "耳机", "airpods", "蓝牙", "降噪", "充电", "连接", "配对",
        "保修", "售后", "维修", "换新", "序列号", "真伪", "电池",
        "airpod", "pro", "max", "苹果", "pineapple",
    ]
    if not any(kw in user_message.lower() for kw in headphone_keywords):
        messages.append({
            "role": "system",
            "content": "注意：用户的问题可能与耳机无关，请礼貌引导回售后主题。",
        })

    # 添加对话历史（最近10轮），将agent角色映射为assistant（DeepSeek只认system/user/assistant）
    for msg in conversation_history[-10:]:
        role = msg["role"]
        if role not in ("user", "assistant", "system"):
            role = "assistant"  # agent等其他角色统一映射为assistant
        messages.append({"role": role, "content": msg["content"]})

    # 添加当前用户消息
    messages.append({"role": "user", "content": user_message})

    # 调用API
    try:
        resp = requests.post(
            DEEPSEEK_API_URL,
            headers={
                "Authorization": f"Bearer {DEEPSEEK_API_KEY}",
                "Content-Type": "application/json",
            },
            json={
                "model": "deepseek-chat",
                "messages": messages,
                "temperature": 0.7,
                "max_tokens": 1000,
            },
            timeout=30,
        )

        if resp.status_code == 200:
            return resp.json()["choices"][0]["message"]["content"]
        else:
            print(f"[AI] DeepSeek error status={resp.status_code} body={resp.text[:200]}")
            return "抱歉，服务暂时繁忙，请稍后再试。"

    except requests.exceptions.Timeout:
        return "抱歉，回复超时，请稍后再试。"
    except requests.exceptions.RequestException:
        return "抱歉，网络出了点问题，请稍后再试。"
    except Exception:
        return "抱歉，系统内部异常，请稍后再试。"
