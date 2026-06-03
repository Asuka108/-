package com.airpods.assistant.model;

import java.util.List;

/** 订单数据模型 */
public class Order {
    private int id;
    private String order_no;
    private String status;
    private String receiver_name;
    private String receiver_phone;
    private String receiver_address;
    private String remark;
    private double total_amount;
    private String paid_at;
    private String created_at;
    private String updated_at;
    private List<OrderItem> items;

    public Order() {}

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getOrder_no() { return order_no; }
    public void setOrder_no(String order_no) { this.order_no = order_no; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReceiver_name() { return receiver_name; }
    public void setReceiver_name(String receiver_name) { this.receiver_name = receiver_name; }

    public String getReceiver_phone() { return receiver_phone; }
    public void setReceiver_phone(String receiver_phone) { this.receiver_phone = receiver_phone; }

    public String getReceiver_address() { return receiver_address; }
    public void setReceiver_address(String receiver_address) { this.receiver_address = receiver_address; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public double getTotal_amount() { return total_amount; }
    public void setTotal_amount(double total_amount) { this.total_amount = total_amount; }

    public String getPaid_at() { return paid_at; }
    public void setPaid_at(String paid_at) { this.paid_at = paid_at; }

    public String getCreated_at() { return created_at; }
    public void setCreated_at(String created_at) { this.created_at = created_at; }

    public String getUpdated_at() { return updated_at; }
    public void setUpdated_at(String updated_at) { this.updated_at = updated_at; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    /** 订单明细项 */
    public static class OrderItem {
        private int id;
        private int product_id;
        private int quantity;
        private String product_name;
        private String product_image;
        private double product_price;

        public OrderItem() {}

        // Getters and Setters
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }

        public int getProduct_id() { return product_id; }
        public void setProduct_id(int product_id) { this.product_id = product_id; }

        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }

        public String getProduct_name() { return product_name; }
        public void setProduct_name(String product_name) { this.product_name = product_name; }

        public String getProduct_image() { return product_image; }
        public void setProduct_image(String product_image) { this.product_image = product_image; }

        public double getProduct_price() { return product_price; }
        public void setProduct_price(double product_price) { this.product_price = product_price; }
    }
}
