"""
产品浏览路由（公开接口）
"""
import os
from fastapi import APIRouter, HTTPException, Query
from database import get_db

router = APIRouter()

BASE_URL = os.getenv("BASE_URL", "http://localhost:8000")


@router.get("/categories")
def list_categories():
    """获取所有产品分类"""
    db = get_db()
    try:
        rows = db.execute(
            "SELECT DISTINCT category FROM products WHERE is_on_sale = 1 AND category IS NOT NULL ORDER BY category"
        ).fetchall()
        return [r["category"] for r in rows]
    finally:
        db.close()


@router.get("")
def list_products(
    category: str = None,
    q: str = None,
    sort: str = "default",
    page: int = Query(1, ge=1),
    page_size: int = Query(100, ge=1, le=500),
):
    """
    产品列表（仅显示上架商品）
    - category: 按分类筛选
    - q: 搜索关键词（匹配名称和描述）
    - sort: 排序方式 default/sales/price_asc/price_desc/newest
    """
    db = get_db()
    try:
        where_clauses = ["is_on_sale = 1"]
        params = []

        if category:
            where_clauses.append("category = %s")
            params.append(category)
        if q:
            where_clauses.append("(name LIKE %s OR description LIKE %s)")
            params.extend([f"%{q}%", f"%{q}%"])

        where_sql = " AND ".join(where_clauses)

        # 排序
        sort_map = {
            "default": "sort_order DESC, id DESC",
            "sales": "sales DESC",
            "price_asc": "price ASC",
            "price_desc": "price DESC",
            "newest": "id DESC",
        }
        order_sql = sort_map.get(sort, sort_map["default"])

        # 总数
        count_row = db.execute(
            f"SELECT COUNT(*) as total FROM products WHERE {where_sql}", params
        ).fetchone()
        total = count_row["total"]

        # 分页查询
        offset = (page - 1) * page_size
        rows = db.execute(
            f"SELECT id, name, model, category, price, original_price, image_url, "
            f"sales, rating, is_on_sale, sort_order "
            f"FROM products WHERE {where_sql} ORDER BY {order_sql} LIMIT %s OFFSET %s",
            params + [page_size, offset],
        ).fetchall()

        # 图片URL补全
        BASE = BASE_URL
        for r in rows:
            if r.get("image_url") and r["image_url"].startswith("/"):
                r["image_url"] = BASE + r["image_url"]

        return {
            "total": total,
            "page": page,
            "page_size": page_size,
            "items": rows,
        }
    finally:
        db.close()


@router.get("/{product_id}")
def get_product(product_id: int):
    """获取产品详情"""
    db = get_db()
    try:
        row = db.execute("SELECT * FROM products WHERE id = %s", (product_id,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="产品不存在")

        # 保持 JSON 字段为原始字符串，Android 端自行解析
        # 空值补默认字符串
        if not row.get("images"):
            row["images"] = "[]"
        if not row.get("specs"):
            row["specs"] = "{}"
        if not row.get("features"):
            row["features"] = "[]"

        # 图片URL转为完整URL，方便APP直接加载
        BASE = BASE_URL
        if row.get("image_url") and row["image_url"].startswith("/"):
            row["image_url"] = BASE + row["image_url"]
        if row.get("images"):
            import json
            try:
                urls = json.loads(row["images"])
                row["images"] = json.dumps([(BASE + u if u.startswith("/") else u) for u in urls])
            except (json.JSONDecodeError, TypeError):
                pass

        return row
    finally:
        db.close()
