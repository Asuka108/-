"""
订单管理路由
"""
import os
import time
from datetime import datetime
from fastapi import APIRouter, HTTPException, Query
from database import get_db
from models import OrderCreateRequest

router = APIRouter()

BASE_URL = os.getenv("BASE_URL", "http://localhost:8000")


def _generate_order_no():
    """生成订单编号：PL + 年月日 + 4位随机数"""
    import random
    ts = time.strftime("%Y%m%d%H%M%S")
    rand = random.randint(1000, 9999)
    return f"PL{ts}{rand}"


@router.post("")
def create_order(req: OrderCreateRequest):
    """从购物车创建订单"""
    db = get_db()
    try:
        # 获取购物车中上架的商品
        cart_items = db.execute(
            "SELECT ci.product_id, ci.quantity, p.name, p.price, p.image_url, p.stock, p.is_on_sale "
            "FROM cart_items ci "
            "JOIN products p ON ci.product_id = p.id "
            "WHERE ci.user_id = %s",
            (req.user_id,),
        ).fetchall()

        if not cart_items:
            raise HTTPException(status_code=400, detail="购物车为空")

        # 校验库存和上架状态
        for item in cart_items:
            if not item["is_on_sale"]:
                raise HTTPException(status_code=400, detail=f"商品 {item['name']} 已下架")
            if item["quantity"] > item["stock"]:
                raise HTTPException(status_code=400, detail=f"商品 {item['name']} 库存不足")

        # 计算总金额
        total_amount = sum(float(item["price"]) * item["quantity"] for item in cart_items)
        total_amount = round(total_amount, 2)

        # 创建订单
        order_no = _generate_order_no()
        cursor = db.execute(
            "INSERT INTO orders (order_no, user_id, total_amount, receiver_name, receiver_phone, receiver_address, remark) "
            "VALUES (%s, %s, %s, %s, %s, %s, %s)",
            (order_no, req.user_id, total_amount, req.receiver_name, req.receiver_phone, req.receiver_address, req.remark),
        )
        order_id = cursor.lastrowid

        # 创建订单明细 + 扣减库存
        for item in cart_items:
            db.execute(
                "INSERT INTO order_items (order_id, product_id, product_name, product_price, product_image, quantity) "
                "VALUES (%s, %s, %s, %s, %s, %s)",
                (order_id, item["product_id"], item["name"], item["price"], item["image_url"], item["quantity"]),
            )
            # 扣减库存、增加销量
            db.execute(
                "UPDATE products SET stock = stock - %s, sales = sales + %s WHERE id = %s",
                (item["quantity"], item["quantity"], item["product_id"]),
            )

        # 清空购物车
        db.execute("DELETE FROM cart_items WHERE user_id = %s", (req.user_id,))

        db.commit()
        return {
            "order_no": order_no,
            "total_amount": total_amount,
            "message": "订单创建成功",
        }
    finally:
        db.close()


@router.get("")
def list_orders(status: str = None, page: int = Query(1, ge=1), page_size: int = Query(10, ge=1, le=50),
                user_id: int = Query(1, ge=1)):
    """订单列表"""
    db = get_db()
    try:
        where_clauses = ["user_id = %s"]
        params = [user_id]

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

        # 为每个订单加载明细
        for order in orders:
            items = db.execute(
                "SELECT * FROM order_items WHERE order_id = %s", (order["id"],)
            ).fetchall()
            for it in items:
                if it.get("product_image") and it["product_image"].startswith("/"):
                    it["product_image"] = BASE_URL + it["product_image"]
            order["items"] = items
            # 格式化时间
            if order.get("created_at"):
                order["created_at"] = str(order["created_at"])
            if order.get("paid_at"):
                order["paid_at"] = str(order["paid_at"])

        return {"total": total, "page": page, "page_size": page_size, "items": orders}
    finally:
        db.close()


@router.get("/{order_no}")
def get_order(order_no: str, user_id: int = Query(1, ge=1)):
    """订单详情"""
    db = get_db()
    try:
        order = db.execute(
            "SELECT * FROM orders WHERE order_no = %s AND user_id = %s",
            (order_no, user_id),
        ).fetchone()
        if not order:
            raise HTTPException(status_code=404, detail="订单不存在")

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

        return order
    finally:
        db.close()


@router.put("/{order_no}/pay")
def pay_order(order_no: str, user_id: int = Query(1, ge=1)):
    """模拟支付"""
    db = get_db()
    try:
        order = db.execute(
            "SELECT id, status FROM orders WHERE order_no = %s AND user_id = %s",
            (order_no, user_id),
        ).fetchone()
        if not order:
            raise HTTPException(status_code=404, detail="订单不存在")
        if order["status"] != "pending":
            raise HTTPException(status_code=400, detail=f"订单状态为 {order['status']}，无法支付")

        db.execute(
            "UPDATE orders SET status = 'paid', paid_at = NOW() WHERE id = %s",
            (order["id"],),
        )
        db.commit()
        return {"message": "支付成功"}
    finally:
        db.close()


@router.put("/{order_no}/cancel")
def cancel_order(order_no: str, user_id: int = Query(1, ge=1)):
    """取消订单"""
    db = get_db()
    try:
        order = db.execute(
            "SELECT id, status FROM orders WHERE order_no = %s AND user_id = %s",
            (order_no, user_id),
        ).fetchone()
        if not order:
            raise HTTPException(status_code=404, detail="订单不存在")
        if order["status"] not in ("pending", "paid"):
            raise HTTPException(status_code=400, detail=f"订单状态为 {order['status']}，无法取消")

        # 恢复库存
        items = db.execute(
            "SELECT product_id, quantity FROM order_items WHERE order_id = %s", (order["id"],)
        ).fetchall()
        for item in items:
            db.execute(
                "UPDATE products SET stock = stock + %s, sales = sales - %s WHERE id = %s",
                (item["quantity"], item["quantity"], item["product_id"]),
            )

        db.execute("UPDATE orders SET status = 'cancelled' WHERE id = %s", (order["id"],))
        db.commit()
        return {"message": "订单已取消"}
    finally:
        db.close()
