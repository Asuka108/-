package com.airpods.assistant.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.airpods.assistant.R;
import com.airpods.assistant.model.Order;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 订单列表 RecyclerView 适配器
 * 展示订单号、状态、商品摘要、金额和时间
 */
public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.OrderHolder> {

    private List<Order> orders = new ArrayList<>();
    private OnOrderClickListener listener;

    public interface OnOrderClickListener {
        void onOrderClick(Order order);
    }

    public void setOnOrderClickListener(OnOrderClickListener listener) {
        this.listener = listener;
    }

    public void setOrders(List<Order> orders) {
        this.orders = orders != null ? orders : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public OrderHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_order, parent, false);
        return new OrderHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderHolder holder, int position) {
        Order order = orders.get(position);

        // 订单号
        holder.tvOrderNo.setText("订单号: " + order.getOrder_no());

        // 状态文字和颜色
        String status = order.getStatus();
        holder.tvStatus.setText(getStatusText(status));
        holder.tvStatus.setTextColor(getStatusColor(status));

        // 商品摘要
        StringBuilder summary = new StringBuilder();
        List<Order.OrderItem> items = order.getItems();
        if (items != null && !items.isEmpty()) {
            for (int i = 0; i < items.size(); i++) {
                Order.OrderItem item = items.get(i);
                summary.append(item.getProduct_name())
                        .append(" x")
                        .append(item.getQuantity());
                if (i < items.size() - 1) {
                    summary.append("、");
                }
            }
        }
        holder.tvItemsSummary.setText(summary.toString());

        // 金额
        holder.tvTotal.setText(String.format(Locale.getDefault(), "¥%.2f", order.getTotal_amount()));

        // 创建时间
        holder.tvCreatedAt.setText(order.getCreated_at());

        // 查看详情按钮
        holder.btnDetail.setOnClickListener(v -> {
            if (listener != null) {
                listener.onOrderClick(order);
            }
        });

        // 整行点击
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onOrderClick(order);
            }
        });
    }

    @Override
    public int getItemCount() {
        return orders.size();
    }

    /** 获取状态显示文字 */
    private String getStatusText(String status) {
        if (status == null) return "未知";
        switch (status) {
            case "pending":   return "待付款";
            case "paid":      return "已付款";
            case "shipped":   return "已发货";
            case "completed": return "已完成";
            case "cancelled": return "已取消";
            default:          return status;
        }
    }

    /** 获取状态颜色 */
    private int getStatusColor(String status) {
        if (status == null) return Color.parseColor("#8E8E93");
        switch (status) {
            case "pending":   return Color.parseColor("#FF9500"); // 橙色
            case "paid":      return Color.parseColor("#007AFF"); // 蓝色
            case "shipped":   return Color.parseColor("#34C759"); // 绿色
            case "completed": return Color.parseColor("#34C759"); // 绿色
            case "cancelled": return Color.parseColor("#8E8E93"); // 灰色
            default:          return Color.parseColor("#8E8E93");
        }
    }

    static class OrderHolder extends RecyclerView.ViewHolder {
        TextView tvOrderNo, tvStatus, tvItemsSummary, tvTotal, tvCreatedAt, btnDetail;

        OrderHolder(View v) {
            super(v);
            tvOrderNo = v.findViewById(R.id.tv_order_no);
            tvStatus = v.findViewById(R.id.tv_status);
            tvItemsSummary = v.findViewById(R.id.tv_items_summary);
            tvTotal = v.findViewById(R.id.tv_total);
            tvCreatedAt = v.findViewById(R.id.tv_created_at);
            btnDetail = v.findViewById(R.id.btn_detail);
        }
    }
}
