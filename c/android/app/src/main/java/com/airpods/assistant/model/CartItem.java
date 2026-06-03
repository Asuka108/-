package com.airpods.assistant.model;

/** 购物车条目数据模型 */
public class CartItem {
    private int id;
    private int product_id;
    private int quantity;
    private String name;
    private String image_url;
    private double price;
    private double original_price;
    private double item_total;
    private int stock;
    private int is_on_sale;       // 1=上架 0=下架
    private String created_at;
    private boolean selected;    // 前端选中状态，不参与网络传输

    public CartItem() {}

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getProduct_id() { return product_id; }
    public void setProduct_id(int product_id) { this.product_id = product_id; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getImage_url() { return image_url; }
    public void setImage_url(String image_url) { this.image_url = image_url; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public double getOriginal_price() { return original_price; }
    public void setOriginal_price(double original_price) { this.original_price = original_price; }

    public double getItem_total() { return item_total; }
    public void setItem_total(double item_total) { this.item_total = item_total; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    public int getIs_on_sale() { return is_on_sale; }
    public void setIs_on_sale(int is_on_sale) { this.is_on_sale = is_on_sale; }

    public String getCreated_at() { return created_at; }
    public void setCreated_at(String created_at) { this.created_at = created_at; }

    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }
}
