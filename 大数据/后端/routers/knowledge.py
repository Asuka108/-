"""
知识库管理路由
GET 接口公开可用，POST/PUT/DELETE 需要管理员权限
"""
from fastapi import APIRouter, HTTPException, Depends, Request
from database import get_db
from models import KnowledgeCreate, KnowledgeUpdate
from services.kb_service import search_knowledge_base
from middleware.admin import admin_required

router = APIRouter()


@router.get("/search")
def search_knowledge(q: str):
    """检索知识库（公开接口）"""
    results = search_knowledge_base(q)
    return {"query": q, "results": results}


@router.get("/items")
def list_knowledge(category: str = None):
    """列出知识条目（公开接口，可按分类筛选）"""
    db = get_db()
    try:
        if category:
            rows = db.execute(
                "SELECT * FROM knowledge_base WHERE category = %s ORDER BY id DESC", (category,)
            ).fetchall()
        else:
            rows = db.execute("SELECT * FROM knowledge_base ORDER BY id DESC").fetchall()
        return [
            {
                "id": r["id"],
                "category": r["category"],
                "question": r["question"],
                "answer": r["answer"],
                "keywords": r["keywords"],
            }
            for r in rows
        ]
    finally:
        db.close()


# ====== 以下接口需要管理员权限 ======

@router.post("/items")
def create_knowledge(item: KnowledgeCreate, _=Depends(admin_required)):
    """新增知识条目（需要管理员权限）"""
    db = get_db()
    try:
        cursor = db.execute(
            "INSERT INTO knowledge_base (category, question, answer, keywords) VALUES (%s, %s, %s, %s)",
            (item.category, item.question, item.answer, item.keywords),
        )
        db.commit()
        return {"id": cursor.lastrowid, "message": "知识条目已添加"}
    finally:
        db.close()


@router.put("/items/{item_id}")
def update_knowledge(item_id: int, item: KnowledgeUpdate, _=Depends(admin_required)):
    """更新知识条目（需要管理员权限）"""
    db = get_db()
    try:
        existing = db.execute("SELECT * FROM knowledge_base WHERE id = %s", (item_id,)).fetchone()
        if not existing:
            raise HTTPException(status_code=404, detail="知识条目不存在")

        updates = {}
        if item.category is not None:
            updates["category"] = item.category
        if item.question is not None:
            updates["question"] = item.question
        if item.answer is not None:
            updates["answer"] = item.answer
        if item.keywords is not None:
            updates["keywords"] = item.keywords

        if updates:
            set_clause = ", ".join(f"{k} = %s" for k in updates.keys())
            db.execute(
                f"UPDATE knowledge_base SET {set_clause}, updated_at = NOW() WHERE id = %s",
                list(updates.values()) + [item_id],
            )
            db.commit()
        return {"message": "知识条目已更新"}
    finally:
        db.close()


@router.delete("/items/{item_id}")
def delete_knowledge(item_id: int, _=Depends(admin_required)):
    """删除知识条目（需要管理员权限）"""
    db = get_db()
    try:
        existing = db.execute("SELECT * FROM knowledge_base WHERE id = %s", (item_id,)).fetchone()
        if not existing:
            raise HTTPException(status_code=404, detail="知识条目不存在")
        db.execute("DELETE FROM knowledge_base WHERE id = %s", (item_id,))
        db.commit()
        return {"message": "知识条目已删除"}
    finally:
        db.close()
