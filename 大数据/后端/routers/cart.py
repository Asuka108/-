"""
购物车路由
"""
import os
from fastapi import APIRouter, HTTPException, Query
from database import get_db
from models import CartAddRequest, CartUpdateRequest

router = APIRouter()

BASE_URL = os.getenv("BASE_URL", "http://localhost:8000")


@router.get("")
def get_cart(user_id: int = Query(1, ge=1)):
    """获取购物车列表"""
    db = get_db()
    try:
        rows = db.execute(
            "SELECT ci.id, ci.product_id, ci.quantity, ci.created_at, "
            "p.name, p.price, p.original_price, p.image_url, p.stock, p.is_on_sale "
            "FROM cart_items ci "
            "JOIN products p ON ci.product_id = p.id "
            "WHERE ci.user_id = %s "
            "ORDER BY ci.created_at DESC",
            (user_id,),
        ).fetchall()

        # 计算总价 & 补全图片URL
        total = 0
        valid_items = []
        for r in rows:
            if r["is_on_sale"]:
                item_total = float(r["price"]) * r["quantity"]
                total += item_total
                r["item_total"] = item_total
                # 图片URL补全
                if r.get("image_url") and r["image_url"].startswith("/"):
                    r["image_url"] = BASE_URL + r["image_url"]
                valid_items.append(r)

        return {
            "items": valid_items,
            "total_count": len(valid_items),
            "total_amount": round(total, 2),
        }
    finally:
        db.close()


@router.post("")
def add_to_cart(req: CartAddRequest):
    """添加商品到购物车"""
    db = get_db()
    try:
        # 检查产品是否存在且上架
        product = db.execute(
            "SELECT id, stock, is_on_sale FROM products WHERE id = %s", (req.product_id,)
        ).fetchone()
        if not product:
            raise HTTPException(status_code=404, detail="产品不存在")
        if not product["is_on_sale"]:
            raise HTTPException(status_code=400, detail="该产品已下架")

        # 检查购物车是否已有该商品
        existing = db.execute(
            "SELECT id, quantity FROM cart_items WHERE user_id = %s AND product_id = %s",
            (req.user_id, req.product_id),
        ).fetchone()

        if existing:
            new_qty = existing["quantity"] + req.quantity
            if new_qty > product["stock"]:
                raise HTTPException(status_code=400, detail="超出库存数量")
            db.execute(
                "UPDATE cart_items SET quantity = %s WHERE id = %s",
                (new_qty, existing["id"]),
            )
        else:
            if req.quantity > product["stock"]:
                raise HTTPException(status_code=400, detail="超出库存数量")
            db.execute(
                "INSERT INTO cart_items (user_id, product_id, quantity) VALUES (%s, %s, %s)",
                (req.user_id, req.product_id, req.quantity),
            )

        db.commit()
        return {"message": "已添加到购物车"}
    finally:
        db.close()


@router.put("/{item_id}")
def update_cart_item(item_id: int, req: CartUpdateRequest, user_id: int = Query(1, ge=1)):
    """更新购物车商品数量"""
    db = get_db()
    try:
        item = db.execute(
            "SELECT ci.id, ci.product_id, p.stock FROM cart_items ci "
            "JOIN products p ON ci.product_id = p.id "
            "WHERE ci.id = %s AND ci.user_id = %s",
            (item_id, user_id),
        ).fetchone()
        if not item:
            raise HTTPException(status_code=404, detail="购物车商品不存在")

        if req.quantity == 0:
            db.execute("DELETE FROM cart_items WHERE id = %s", (item_id,))
        else:
            if req.quantity > item["stock"]:
                raise HTTPException(status_code=400, detail="超出库存数量")
            db.execute(
                "UPDATE cart_items SET quantity = %s WHERE id = %s",
                (req.quantity, item_id),
            )

        db.commit()
        return {"message": "购物车已更新"}
    finally:
        db.close()


@router.delete("/{item_id}")
def delete_cart_item(item_id: int, user_id: int = Query(1, ge=1)):
    """删除购物车商品"""
    db = get_db()
    try:
        item = db.execute(
            "SELECT id FROM cart_items WHERE id = %s AND user_id = %s",
            (item_id, user_id),
        ).fetchone()
        if not item:
            raise HTTPException(status_code=404, detail="购物车商品不存在")

        db.execute("DELETE FROM cart_items WHERE id = %s", (item_id,))
        db.commit()
        return {"message": "已从购物车移除"}
    finally:
        db.close()


@router.delete("")
def clear_cart(user_id: int = Query(1, ge=1)):
    """清空购物车"""
    db = get_db()
    try:
        db.execute("DELETE FROM cart_items WHERE user_id = %s", (user_id,))
        db.commit()
        return {"message": "购物车已清空"}
    finally:
        db.close()
