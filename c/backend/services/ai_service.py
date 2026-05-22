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

    参数:
        user_message: 用户当前消息
        conversation_history: 之前的对话记录 [{"role": "user/assistant", "content": "..."}]
        knowledge_context: 从知识库检索到的相关内容
        system_prompt: 系统提示词
    """
    # 构造消息列表
    messages = [{"role": "system", "content": system_prompt}]

    # 注入知识库上下文
    if knowledge_context:
        messages.append({
            "role": "system",
            "content": f"【参考知识库内容，优先据此回答】\n{knowledge_context}",
        })

    # 注入关键词预检：检测用户是否问了无关问题
    headphone_keywords = [
        "耳机", "airpods", "蓝牙", "充电", "配对", "降噪", "音质",
        "续航", "保修", "售后", "真假", "序列号", "无声", "连接",
        "airpod", "pro", "max", "菠萝", "pineapple",
    ]
    if not any(kw in user_message.lower() for kw in headphone_keywords):
        messages.append({
            "role": "system",
            "content": "注意：用户可能问了与耳机无关的问题，请礼貌引导回耳机售后话题。",
        })

    # 添加对话历史（最近10轮）
    for msg in conversation_history[-10:]:
        messages.append({"role": msg["role"], "content": msg["content"]})

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
            return "抱歉，服务暂时繁忙，请稍后再试。"

    except requests.exceptions.Timeout:
        return "抱歉，回复超时，请稍后重试。"
    except requests.exceptions.RequestException:
        return "抱歉，网络出了点问题，请稍后重试。"
    except Exception:
        return "抱歉，系统出现异常，请稍后重试。"
