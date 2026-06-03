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
    # 产品 & 配件
    "保护壳", "壳", "耳塞", "耳垫", "挂绳", "清洁", "套件",
    "充电器", "充电线", "电源", "适配器", "usb", "lightning", "earpods",
    "有线", "头戴", "入耳", "半入耳", "心率", "健康", "运动",
    "买", "卖", "推荐", "选购", "哪款", "哪个好", "值得",
    "商城", "产品", "配件", "型号", "规格",
}


def _score_items(query_words: set, items: list, keywords_field: str = "keywords") -> list:
    """通用评分函数：计算查询词与知识条目的匹配得分"""
    scored = []
    for item in items:
        keywords = item[keywords_field] if isinstance(item, dict) else item["keywords"]
        if not keywords:
            continue
        kw_set = set(kw.strip() for kw in keywords.split(","))
        score = len(query_words & kw_set)
        if score > 0:
            scored.append((score, item))
    scored.sort(key=lambda x: x[0], reverse=True)
    return scored[:3]


def _format_results(top_n: list) -> str:
    """通用结果格式化"""
    results = []
    for score, item in top_n:
        cat = item["category"] if isinstance(item, dict) else item["category"]
        q = item["question"] if isinstance(item, dict) else item["question"]
        a = item["answer"] if isinstance(item, dict) else item["answer"]
        results.append(f"【{cat}】\n问：{q}\n答：{a}\n")
    return "\n---\n".join(results)


def search_knowledge_base(query: str, db=None) -> str:
    """
    检索知识库，返回匹配度最高的3条结果

    参数:
        query: 用户查询文本
        db: 可选的数据库连接（复用外部连接），为None时自行创建
    返回:
        匹配的知识库内容文本（最多3条），无匹配返回空字符串
    """
    # 先检查是否与耳机相关
    query_lower = query.lower()
    is_related = any(kw in query_lower for kw in HEADPHONE_KEYWORDS)
    if not is_related:
        return ""

    # 中文分词提取查询关键词
    query_words = set(jieba.cut(query))
    query_words.update(query)

    # 判断是否需要自己管理连接
    own_db = db is None
    if own_db:
        db = get_db()

    try:
        all_items = db.execute("SELECT * FROM knowledge_base").fetchall()

        if not all_items:
            return _search_from_json(query_words)

        scored = _score_items(query_words, all_items)
        if not scored:
            return ""
        return _format_results(scored)
    finally:
        if own_db:
            db.close()


def _search_from_json(query_words: set) -> str:
    """从JSON文件检索（数据库为空时使用）"""
    if not os.path.exists(KB_JSON_PATH):
        return ""

    with open(KB_JSON_PATH, "r", encoding="utf-8") as f:
        kb_data = json.load(f)

    scored = _score_items(query_words, kb_data)
    if not scored:
        return ""
    return _format_results(scored)


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
                "SELECT id FROM knowledge_base WHERE question = %s", (item["question"],)
            ).fetchone()
            if not existing:
                db.execute(
                    "INSERT INTO knowledge_base (category, question, answer, keywords) VALUES (%s, %s, %s, %s)",
                    (item["category"], item["question"], item["answer"], item["keywords"]),
                )
        db.commit()
    finally:
        db.close()
