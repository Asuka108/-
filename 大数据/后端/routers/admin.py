"""
管理后台路由（产品管理 + 图片上传 + 订单查看 + 统计）
使用 Token 认证，支持多 worker 部署
"""
import os
import uuid
import json
import hmac
import hashlib
from datetime import datetime
from fastapi import APIRouter, HTTPException, UploadFile, File, Form, Header
from database import get_db
from models import ProductCreate, ProductUpdate, AdminLoginRequest

router = APIRouter()

# 上传目录
UPLOAD_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), "static", "uploads")

# 服务器地址
BASE_URL = os.getenv("BASE_URL", "http://localhost:8000")


def _generate_token(username: str, password: str) -> str:
    """根据用户名+密码生成固定 token"""
    return hmac.new(b"pineapple-admin", f"{username}:{password}".encode(), hashlib.sha256).hexdigest()[:32]


def _check_admin_token(token: str = None):
    """验证管理员 Token（检查数据库中是否存在匹配的管理员）"""
    if not token:
        raise HTTPException(status_code=401, detail="请先登录管理后台")
    # 通过 token 反查：遍历管理员生成 token 匹配
    db = get_db()
    try:
        admins = db.execute("SELECT username, password_hash FROM admin_users").fetchall()
        for admin in admins:
            expected = _generate_token(admin["username"], admin["password_hash"])
            if hmac.compare_digest(token, expected):
                return  # 验证通过
        raise HTTPException(status_code=401, detail="请先登录管理后台")
    finally:
        db.close()


# ====== 登录 ======

@router.post("/login")
def admin_login(req: AdminLoginRequest):
    """管理员登录（用户名+密码），返回 token"""
    db = get_db()
    try:
        pwd_hash = hashlib.sha256(req.password.encode()).hexdigest()
        admin = db.execute(
            "SELECT id, username, nickname FROM admin_users WHERE username = %s AND password_hash = %s",
            (req.username, pwd_hash),
        ).fetchone()
        if not admin:
            raise HTTPException(status_code=401, detail="用户名或密码错误")
        token = _generate_token(req.username, pwd_hash)
        return {"message": "登录成功", "token": token, "nickname": admin["nickname"]}
    finally:
        db.close()


# ====== 产品管理 ======

@router.get("/products")
def admin_list_products(
    category: str = None,
    q: str = None,
    is_on_sale: int = None,
    page: int = 1,
    page_size: int = 20,
    x_admin_token: str = Header(None),
):
    """管理员查看产品列表（含下架商品）"""
    _check_admin_token(x_admin_token)
    db = get_db()
    try:
        where_clauses = ["1=1"]
        params = []

        if category:
            where_clauses.append("category = %s")
            params.append(category)
        if q:
            where_clauses.append("(name LIKE %s OR description LIKE %s)")
            params.extend([f"%{q}%", f"%{q}%"])
        if is_on_sale is not None:
            where_clauses.append("is_on_sale = %s")
            params.append(is_on_sale)

        where_sql = " AND ".join(where_clauses)

        count_row = db.execute(
            f"SELECT COUNT(*) as total FROM products WHERE {where_sql}", params
        ).fetchone()
        total = count_row["total"]

        offset = (page - 1) * page_size
        rows = db.execute(
            f"SELECT * FROM products WHERE {where_sql} ORDER BY sort_order DESC, id DESC LIMIT %s OFFSET %s",
            params + [page_size, offset],
        ).fetchall()

        for r in rows:
            if r.get("created_at"):
                r["created_at"] = str(r["created_at"])
            if r.get("updated_at"):
                r["updated_at"] = str(r["updated_at"])

        return {"total": total, "page": page, "page_size": page_size, "items": rows}
    finally:
        db.close()


@router.post("/products")
def admin_create_product(
    name: str = Form(...),
    model: str = Form(None),
    category: str = Form(None),
    description: str = Form(None),
    price: float = Form(...),
    original_price: float = Form(None),
    image_url: str = Form(None),
    images: str = Form(None),
    stock: int = Form(100),
    specs: str = Form(None),
    features: str = Form(None),
    is_on_sale: int = Form(1),
    sort_order: int = Form(0),
    x_admin_token: str = Header(None),
):
    """添加产品"""
    _check_admin_token(x_admin_token)
    db = get_db()
    try:
        cursor = db.execute(
            "INSERT INTO products (name, model, category, description, price, original_price, "
            "image_url, images, stock, specs, features, is_on_sale, sort_order) "
            "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)",
            (name, model, category, description, price, original_price,
             image_url, images, stock, specs, features, is_on_sale, sort_order),
        )
        db.commit()
        return {"id": cursor.lastrowid, "message": "产品添加成功"}
    finally:
        db.close()


@router.put("/products/{product_id}")
def admin_update_product(
    product_id: int,
    product: ProductUpdate,
    x_admin_token: str = Header(None),
):
    """编辑产品"""
    _check_admin_token(x_admin_token)
    db = get_db()
    try:
        existing = db.execute("SELECT id FROM products WHERE id = %s", (product_id,)).fetchone()
        if not existing:
            raise HTTPException(status_code=404, detail="产品不存在")

        updates = {}
        for field in ["name", "model", "category", "description", "price", "original_price",
                       "image_url", "images", "stock", "specs", "features", "is_on_sale", "sort_order"]:
            val = getattr(product, field, None)
            if val is not None:
                updates[field] = val

        if updates:
            set_clause = ", ".join(f"{k} = %s" for k in updates.keys())
            db.execute(
                f"UPDATE products SET {set_clause}, updated_at = NOW() WHERE id = %s",
                list(updates.values()) + [product_id],
            )
            db.commit()
        return {"message": "产品更新成功"}
    finally:
        db.close()


@router.delete("/products/{product_id}")
def admin_delete_product(product_id: int, x_admin_token: str = Header(None)):
    """删除产品"""
    _check_admin_token(x_admin_token)
    db = get_db()
    try:
        existing = db.execute("SELECT id FROM products WHERE id = %s", (product_id,)).fetchone()
        if not existing:
            raise HTTPException(status_code=404, detail="产品不存在")
        db.execute("DELETE FROM products WHERE id = %s", (product_id,))
        db.commit()
        return {"message": "产品已删除"}
    finally:
        db.close()


@router.put("/products/{product_id}/toggle")
def admin_toggle_product(product_id: int, x_admin_token: str = Header(None)):
    """上架/下架切换"""
    _check_admin_token(x_admin_token)
    db = get_db()
    try:
        existing = db.execute(
            "SELECT id, is_on_sale FROM products WHERE id = %s", (product_id,)
        ).fetchone()
        if not existing:
            raise HTTPException(status_code=404, detail="产品不存在")

        new_status = 0 if existing["is_on_sale"] else 1
        db.execute("UPDATE products SET is_on_sale = %s WHERE id = %s", (new_status, product_id))
        db.commit()
        return {"message": "已上架" if new_status else "已下架", "is_on_sale": new_status}
    finally:
        db.close()


# ====== 图片上传 ======

@router.post("/upload")
async def admin_upload_image(
    file: UploadFile = File(...),
    x_admin_token: str = Header(None),
):
    """上传图片"""
    _check_admin_token(x_admin_token)

    allowed_types = {"image/jpeg", "image/png", "image/gif", "image/webp"}
    if file.content_type not in allowed_types:
        raise HTTPException(status_code=400, detail="仅支持 JPG/PNG/GIF/WEBP 格式")

    contents = await file.read()
    if len(contents) > 10 * 1024 * 1024:
        raise HTTPException(status_code=400, detail="文件大小不能超过 10MB")

    ext = file.filename.split(".")[-1] if "." in file.filename else "jpg"
    filename = f"{uuid.uuid4().hex}.{ext}"
    filepath = os.path.join(UPLOAD_DIR, filename)

    os.makedirs(UPLOAD_DIR, exist_ok=True)
    with open(filepath, "wb") as f:
        f.write(contents)

    url = f"/static/uploads/{filename}"
    return {"url": url, "filename": filename}


# ====== 订单管理 ======

@router.get("/orders")
def admin_list_orders(
    status: str = None,
    page: int = 1,
    page_size: int = 20,
    x_admin_token: str = Header(None),
):
    """查看所有订单"""
    _check_admin_token(x_admin_token)
    db = get_db()
    try:
        where_clauses = ["1=1"]
        params = []

        if status:
            where_clauses.append("status = %s")
            params.append(status)

        where_sql = " AND ".join(where_clauses)

        count_row = db.execute(
            f"SELECT COUNT(*) as total FROM orders WHERE {where_sql}", params
        ).fetchone()
        total = count_row["total"]

        offset = (page - 1) * page_size
        orders = db.execute(
            f"SELECT * FROM orders WHERE {where_sql} ORDER BY created_at DESC LIMIT %s OFFSET %s",
            params + [page_size, offset],
        ).fetchall()

        for order in orders:
            items = db.execute(
                "SELECT * FROM order_items WHERE order_id = %s", (order["id"],)
            ).fetchall()
            for it in items:
                if it.get("product_image") and it["product_image"].startswith("/"):
                    it["product_image"] = BASE_URL + it["product_image"]
            order["items"] = items
            if order.get("created_at"):
                order["created_at"] = str(order["created_at"])
            if order.get("paid_at"):
                order["paid_at"] = str(order["paid_at"])

        return {"total": total, "page": page, "page_size": page_size, "items": orders}
    finally:
        db.close()


@router.put("/orders/{order_no}/ship")
def admin_ship_order(order_no: str, x_admin_token: str = Header(None)):
    """发货"""
    _check_admin_token(x_admin_token)
    db = get_db()
    try:
        order = db.execute("SELECT id, status FROM orders WHERE order_no = %s", (order_no,)).fetchone()
        if not order:
            raise HTTPException(status_code=404, detail="订单不存在")
        if order["status"] != "paid":
            raise HTTPException(status_code=400, detail="只有已支付的订单才能发货")

        db.execute("UPDATE orders SET status = 'shipped' WHERE id = %s", (order["id"],))
        db.commit()
        return {"message": "发货成功"}
    finally:
        db.close()


# ====== 统计数据 ======

@router.get("/stats")
def admin_stats(x_admin_token: str = Header(None)):
    """管理后台统计数据"""
    _check_admin_token(x_admin_token)
    db = get_db()
    try:
        products_count = db.execute("SELECT COUNT(*) as c FROM products").fetchone()["c"]
        orders_count = db.execute("SELECT COUNT(*) as c FROM orders").fetchone()["c"]
        paid_revenue = db.execute(
            "SELECT COALESCE(SUM(total_amount), 0) as s FROM orders WHERE status IN ('paid', 'shipped', 'completed')"
        ).fetchone()["s"]
        pending_orders = db.execute(
            "SELECT COUNT(*) as c FROM orders WHERE status = 'pending'"
        ).fetchone()["c"]

        return {
            "products_count": products_count,
            "orders_count": orders_count,
            "total_revenue": float(paid_revenue),
            "pending_orders": pending_orders,
        }
    finally:
        db.close()
