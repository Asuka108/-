package com.airpods.assistant.model;

/** 商品数据模型 */
public class Product {
    private int id;
    private String name;
    private String model;
    private String category;
    private String description;
    private String image_url;
    private double price;
    private double original_price;
    private int stock;
    private int sales;
    private int is_on_sale;       // 1=上架 0=下架
    private int sort_order;
    private double rating;
    private String images;        // JSON字符串
    private String specs;         // JSON字符串
    private String features;      // JSON字符串
    private String created_at;
    private String updated_at;

    public Product() {}

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImage_url() { return image_url; }
    public void setImage_url(String image_url) { this.image_url = image_url; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public double getOriginal_price() { return original_price; }
    public void setOriginal_price(double original_price) { this.original_price = original_price; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    public int getSales() { return sales; }
    public void setSales(int sales) { this.sales = sales; }

    public int getIs_on_sale() { return is_on_sale; }
    public void setIs_on_sale(int is_on_sale) { this.is_on_sale = is_on_sale; }

    public int getSort_order() { return sort_order; }
    public void setSort_order(int sort_order) { this.sort_order = sort_order; }

    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }

    public String getImages() { return images; }
    public void setImages(String images) { this.images = images; }

    public String getSpecs() { return specs; }
    public void setSpecs(String specs) { this.specs = specs; }

    public String getFeatures() { return features; }
    public void setFeatures(String features) { this.features = features; }

    public String getCreated_at() { return created_at; }
    public void setCreated_at(String created_at) { this.created_at = created_at; }

    public String getUpdated_at() { return updated_at; }
    public void setUpdated_at(String updated_at) { this.updated_at = updated_at; }
}
