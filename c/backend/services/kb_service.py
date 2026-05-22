"""
知识库检索服务
使用jieba分词 + 关键词匹配在知识库中检索相关内容
"""
import os
import json
import jieba
from database import get_db

# 知识库JSON文件路径（初始数据源）
KB_JSON_PATH = os.path.join(os.path.dirname(__file__), "..", "knowledge_base.json")

# 菠萝耳机相关关键词（用于过滤无关查询）
HEADPHONE_KEYWORDS = {
    "耳机", "airpods", "蓝牙", "充电", "配对", "连接", "降噪", "通透",
    "音质", "续航", "电池", "保修", "售后", "真假", "序列号", "识别",
    "无声", "单耳", "右耳", "左耳", "没声音", "杂音", "音量",
    "siri", "触控", "重置", "恢复", "进水", "摔", "坏",
    "pro", "max", "菠萝", "pineapple", "区别", "对比", "价格",
    "退换", "退货", "换货", "维修", "以旧换新", "care",
}


def search_knowledge_base(query: str) -> str:
    """
    检索知识库，返回匹配度最高的3条结果

    参数:
        query: 用户查询文本
    返回:
        匹配的知识库内容文本（最多3条），无匹配返回空字符串
    """
    # 先检查是否与耳机相关
    query_lower = query.lower()
    is_related = any(kw in query_lower for kw in HEADPHONE_KEYWORDS)
    if not is_related:
        return ""  # 无关查询不检索知识库

    db = get_db()
    try:
        all_items = db.execute("SELECT * FROM knowledge_base").fetchall()

        if not all_items:
            # 数据库为空时从JSON文件加载
            return _search_from_json(query)

        # 中文分词提取查询关键词
        query_words = set(jieba.cut(query))
        query_words.update(query)  # 保留原始查询

        # 计算每条知识的关键词匹配得分
        scored = []
        for item in all_items:
            keywords = item["keywords"] or ""
            kw_set = set(kw.strip() for kw in keywords.split(","))
            # 匹配得分 = 查询词与知识库关键词的交集大小
            score = len(query_words & kw_set)
            if score > 0:
                scored.append((score, item))

        # 按得分降序排列，取前3条
        scored.sort(key=lambda x: x[0], reverse=True)
        top_n = scored[:3]

        if not top_n:
            return ""

        # 拼接结果
        results = []
        for score, item in top_n:
            results.append(
                f"【{item['category']}】\n"
                f"问：{item['question']}\n"
                f"答：{item['answer']}\n"
            )
        return "\n---\n".join(results)
    finally:
        db.close()


def _search_from_json(query: str) -> str:
    """从JSON文件检索（数据库为空时使用）"""
    if not os.path.exists(KB_JSON_PATH):
        return ""

    with open(KB_JSON_PATH, "r", encoding="utf-8") as f:
        kb_data = json.load(f)

    query_words = set(jieba.cut(query))
    scored = []
    for item in kb_data:
        keywords = item.get("keywords", "")
        kw_set = set(kw.strip() for kw in keywords.split(","))
        score = len(query_words & kw_set)
        if score > 0:
            scored.append((score, item))

    scored.sort(key=lambda x: x[0], reverse=True)
    top_n = scored[:3]

    if not top_n:
        return ""

    results = []
    for score, item in top_n:
        results.append(
            f"【{item['category']}】\n"
            f"问：{item['question']}\n"
            f"答：{item['answer']}\n"
        )
    return "\n---\n".join(results)


def load_kb_from_json_to_db():
    """将knowledge_base.json的数据导入数据库"""
    if not os.path.exists(KB_JSON_PATH):
        return

    with open(KB_JSON_PATH, "r", encoding="utf-8") as f:
        kb_data = json.load(f)

    db = get_db()
    try:
        for item in kb_data:
            existing = db.execute(
                "SELECT id FROM knowledge_base WHERE question = ?", (item["question"],)
            ).fetchone()
            if not existing:
                db.execute(
                    "INSERT INTO knowledge_base (category, question, answer, keywords) VALUES (?, ?, ?, ?)",
                    (item["category"], item["question"], item["answer"], item["keywords"]),
                )
        db.commit()
    finally:
        db.close()
