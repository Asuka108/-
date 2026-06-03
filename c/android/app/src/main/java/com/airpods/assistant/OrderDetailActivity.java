package com.airpods.assistant;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.airpods.assistant.api.ApiClient;
import com.airpods.assistant.model.Order;
import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * 订单详情页面
 * 展示订单完整信息，支持支付和取消操作
 */
public class OrderDetailActivity extends AppCompatActivity {

    private static final String TAG = "OrderDetailActivity";

    private LinearLayout bannerStatus;
    private TextView tvStatus;
    private TextView tvOrderNo, tvCreatedAt, tvPaidAt;
    private LinearLayout rowPaidAt;
    private TextView tvReceiverName, tvReceiverPhone, tvReceiverAddress;
    private View cardRemark;
    private TextView tvRemark;
    private LinearLayout itemsContainer;
    private TextView tvTotal;
    private LinearLayout bottomBar;
    private Button btnPay, btnCancel;

    private String orderNo;
    private Order currentOrder;

    private final Gson gson = new Gson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_detail);

        orderNo = getIntent().getStringExtra("order_no");
        if (orderNo == null || orderNo.isEmpty()) {
            Toast.makeText(this, "订单号无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        loadOrderDetail();
    }

    private void initViews() {
        bannerStatus = findViewById(R.id.banner_status);
        tvStatus = findViewById(R.id.tv_status);
        tvOrderNo = findViewById(R.id.tv_order_no);
        tvCreatedAt = findViewById(R.id.tv_created_at);
        tvPaidAt = findViewById(R.id.tv_paid_at);
        rowPaidAt = findViewById(R.id.row_paid_at);
        tvReceiverName = findViewById(R.id.tv_receiver_name);
        tvReceiverPhone = findViewById(R.id.tv_receiver_phone);
        tvReceiverAddress = findViewById(R.id.tv_receiver_address);
        cardRemark = findViewById(R.id.card_remark);
        tvRemark = findViewById(R.id.tv_remark);
        itemsContainer = findViewById(R.id.items_container);
        tvTotal = findViewById(R.id.tv_total);
        bottomBar = findViewById(R.id.bottom_bar);
        btnPay = findViewById(R.id.btn_pay);
        btnCancel = findViewById(R.id.btn_cancel);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // 支付按钮蓝色圆角背景
        GradientDrawable payBg = new GradientDrawable();
        payBg.setColor(Color.parseColor("#007AFF"));
        payBg.setCornerRadius(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()));
        btnPay.setBackground(payBg);

        // 取消按钮描边背景
        GradientDrawable cancelBg = new GradientDrawable();
        cancelBg.setColor(Color.TRANSPARENT);
        cancelBg.setStroke((int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics()),
                Color.parseColor("#8E8E93"));
        cancelBg.setCornerRadius(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()));
        btnCancel.setBackground(cancelBg);

        btnPay.setOnClickListener(v -> payOrder());
        btnCancel.setOnClickListener(v -> confirmCancelOrder());
    }

    /** 加载订单详情 */
    private void loadOrderDetail() {
        int uid = resolveUserId();
        Map<String, String> params = new HashMap<>();
        params.put("user_id", String.valueOf(uid));
        ApiClient.getInstance().get("/orders/" + orderNo, params, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to load order detail", e);
                mainHandler.post(() ->
                        Toast.makeText(OrderDetailActivity.this,
                                "加载订单失败", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    mainHandler.post(() ->
                            Toast.makeText(OrderDetailActivity.this,
                                    "加载失败 (" + response.code() + ")", Toast.LENGTH_SHORT).show());
                    return;
                }

                try {
                    String json = response.body().string();
                    Order order = parseOrder(json);
                    if (order != null) {
                        mainHandler.post(() -> {
                            currentOrder = order;
                            displayOrder(order);
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing order detail", e);
                    mainHandler.post(() ->
                            Toast.makeText(OrderDetailActivity.this,
                                    "数据解析失败", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    /** 展示订单信息 */
    private void displayOrder(Order order) {
        // 状态横幅
        String status = order.getStatus();
        bannerStatus.setBackgroundColor(getStatusBannerColor(status));
        tvStatus.setText(getStatusText(status));

        // 订单信息
        tvOrderNo.setText(order.getOrder_no());
        tvCreatedAt.setText(order.getCreated_at());

        // 支付时间
        if (order.getPaid_at() != null && !order.getPaid_at().isEmpty()) {
            rowPaidAt.setVisibility(View.VISIBLE);
            tvPaidAt.setText(order.getPaid_at());
        } else {
            rowPaidAt.setVisibility(View.GONE);
        }

        // 收货人信息
        tvReceiverName.setText(order.getReceiver_name());
        tvReceiverPhone.setText(order.getReceiver_phone());
        tvReceiverAddress.setText(order.getReceiver_address());

        // 备注
        if (order.getRemark() != null && !order.getRemark().isEmpty()) {
            cardRemark.setVisibility(View.VISIBLE);
            tvRemark.setText(order.getRemark());
        } else {
            cardRemark.setVisibility(View.GONE);
        }

        // 商品清单
        displayOrderItems(order.getItems());

        // 合计
        tvTotal.setText(String.format(Locale.getDefault(), "¥%.2f", order.getTotal_amount()));

        // 底部按钮（根据状态显示）
        updateBottomBar(status);
    }

    /** 展示订单商品列表 */
    private void displayOrderItems(List<Order.OrderItem> items) {
        itemsContainer.removeAllViews();
        if (items == null) return;

        for (Order.OrderItem item : items) {
            View row = createItemRow(item);
            itemsContainer.addView(row);
        }
    }

    /** 创建单个商品行 */
    private View createItemRow(Order.OrderItem item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        int marginBottom = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
        rowParams.setMargins(0, 0, 0, marginBottom);
        row.setLayoutParams(rowParams);

        // 商品图片
        int imageSize = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 60, getResources().getDisplayMetrics());
        ImageView ivImage = new ImageView(this);
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(imageSize, imageSize);
        imgParams.setMarginEnd((int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics()));
        ivImage.setLayoutParams(imgParams);
        ivImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivImage.setBackgroundColor(Color.parseColor("#F2F2F7"));

        String imageUrl = item.getProduct_image();
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(this)
                    .load(imageUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .centerCrop()
                    .into(ivImage);
        } else {
            ivImage.setImageResource(android.R.drawable.ic_menu_gallery);
        }
        row.addView(ivImage);

        // 中间：名称 + 数量
        LinearLayout midCol = new LinearLayout(this);
        midCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams midParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        midCol.setLayoutParams(midParams);

        TextView tvName = new TextView(this);
        tvName.setText(item.getProduct_name());
        tvName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvName.setTextColor(Color.parseColor("#1C1C1E"));
        tvName.setMaxLines(2);
        tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        midCol.addView(tvName);

        TextView tvQty = new TextView(this);
        tvQty.setText("x" + item.getQuantity());
        tvQty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvQty.setTextColor(Color.parseColor("#8E8E93"));
        LinearLayout.LayoutParams qtyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        qtyParams.topMargin = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());
        tvQty.setLayoutParams(qtyParams);
        midCol.addView(tvQty);

        row.addView(midCol);

        // 右侧：价格
        TextView tvPrice = new TextView(this);
        tvPrice.setText(String.format(Locale.getDefault(), "¥%.2f", item.getProduct_price()));
        tvPrice.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvPrice.setTextColor(Color.parseColor("#FF3B30"));
        tvPrice.setTypeface(null, Typeface.BOLD);
        row.addView(tvPrice);

        return row;
    }

    /** 根据订单状态更新底部按钮 */
    private void updateBottomBar(String status) {
        if ("pending".equals(status)) {
            bottomBar.setVisibility(View.VISIBLE);
            btnPay.setVisibility(View.VISIBLE);
            btnCancel.setVisibility(View.VISIBLE);
            btnPay.setText("去支付");
            btnCancel.setText("取消订单");
        } else if ("paid".equals(status)) {
            bottomBar.setVisibility(View.VISIBLE);
            btnPay.setVisibility(View.VISIBLE);
            btnCancel.setVisibility(View.GONE);
            btnPay.setText("已支付");
            btnPay.setEnabled(false);
            // 已支付按钮灰色
            GradientDrawable paidBg = new GradientDrawable();
            paidBg.setColor(Color.parseColor("#C7C7CC"));
            paidBg.setCornerRadius(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()));
            btnPay.setBackground(paidBg);
        } else {
            bottomBar.setVisibility(View.GONE);
        }
    }

    /** 支付订单 */
    private void payOrder() {
        if (currentOrder == null) return;
        btnPay.setEnabled(false);

        int uid = resolveUserId();
        ApiClient.getInstance().put("/orders/" + orderNo + "/pay?user_id=" + uid, new HashMap<>(), new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to pay order", e);
                mainHandler.post(() -> {
                    btnPay.setEnabled(true);
                    Toast.makeText(OrderDetailActivity.this,
                            "支付请求失败", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                mainHandler.post(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(OrderDetailActivity.this,
                                "支付成功", Toast.LENGTH_SHORT).show();
                        loadOrderDetail();
                    } else {
                        btnPay.setEnabled(true);
                        Toast.makeText(OrderDetailActivity.this,
                                "支付失败 (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    /** 确认取消订单 */
    private void confirmCancelOrder() {
        new AlertDialog.Builder(this)
                .setTitle("取消订单")
                .setMessage("确定要取消该订单吗？")
                .setPositiveButton("确定取消", (dialog, which) -> cancelOrder())
                .setNegativeButton("再想想", null)
                .show();
    }

    /** 取消订单 */
    private void cancelOrder() {
        if (currentOrder == null) return;

        int uid = resolveUserId();
        ApiClient.getInstance().put("/orders/" + orderNo + "/cancel?user_id=" + uid, new HashMap<>(), new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to cancel order", e);
                mainHandler.post(() ->
                        Toast.makeText(OrderDetailActivity.this,
                                "取消请求失败", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                mainHandler.post(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(OrderDetailActivity.this,
                                "订单已取消", Toast.LENGTH_SHORT).show();
                        loadOrderDetail();
                    } else {
                        Toast.makeText(OrderDetailActivity.this,
                                "取消失败 (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    /** 解析单个订单 */
    private Order parseOrder(String json) {
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root.has("data") && root.get("data").isJsonObject()) {
                return gson.fromJson(root.getAsJsonObject("data"), Order.class);
            }
            return gson.fromJson(root, Order.class);
        } catch (Exception e) {
            Log.e(TAG, "parseOrder error", e);
            return null;
        }
    }

    private int resolveUserId() {
        int uid = ApiClient.getInstance().getUserId();
        if (uid <= 0) {
            SharedPreferences sp = getSharedPreferences("auth", MODE_PRIVATE);
            uid = sp.getInt("user_id", 1);
        }
        return uid > 0 ? uid : 1;
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

    /** 获取状态横幅背景色 */
    private int getStatusBannerColor(String status) {
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
}
