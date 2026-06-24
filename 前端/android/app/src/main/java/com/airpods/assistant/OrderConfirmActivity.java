package com.airpods.assistant;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.airpods.assistant.api.ApiClient;
import com.airpods.assistant.model.CartItem;
import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * 确认订单页面
 * 展示购物车商品、填写收货信息、提交订单
 */
public class OrderConfirmActivity extends AppCompatActivity {

    private static final String TAG = "OrderConfirmActivity";

    private EditText etName, etPhone, etAddress, etRemark;
    private LinearLayout itemsContainer;
    private TextView tvCount, tvTotal;
    private TextView btnSubmit;

    private final List<CartItem> cartItems = new ArrayList<>();
    private final Gson gson = new Gson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_confirm);

        initViews();
        loadCartItems();
    }

    private void initViews() {
        etName = findViewById(R.id.et_name);
        etPhone = findViewById(R.id.et_phone);
        etAddress = findViewById(R.id.et_address);
        etRemark = findViewById(R.id.et_remark);
        itemsContainer = findViewById(R.id.items_container);
        tvCount = findViewById(R.id.tv_count);
        tvTotal = findViewById(R.id.tv_total);
        btnSubmit = findViewById(R.id.btn_submit);

        // 设置提交按钮蓝色圆角背景 (SUSE-OAA-APP style: 12dp radius)
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor("#007AFF"));
        btnBg.setCornerRadius(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics()));
        btnSubmit.setBackground(btnBg);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        btnSubmit.setOnClickListener(v -> submitOrder());
    }

    /** 加载购物车商品 */
    private void loadCartItems() {
        int uid = resolveUserId();
        Map<String, String> params = new HashMap<>();
        params.put("user_id", String.valueOf(uid));
        ApiClient.getInstance().get("/cart", params, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to load cart", e);
                mainHandler.post(() ->
                        Toast.makeText(OrderConfirmActivity.this,
                                "加载购物车失败", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    mainHandler.post(() ->
                            Toast.makeText(OrderConfirmActivity.this,
                                    "加载失败 (" + response.code() + ")", Toast.LENGTH_SHORT).show());
                    return;
                }

                try {
                    String json = response.body().string();
                    List<CartItem> parsed = parseCartList(json);
                    mainHandler.post(() -> {
                        cartItems.clear();
                        cartItems.addAll(parsed);
                        displayCartItems();
                        updateSummary();
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing cart", e);
                    mainHandler.post(() ->
                            Toast.makeText(OrderConfirmActivity.this,
                                    "数据解析失败", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    /** 在页面中展示购物车商品行 */
    private void displayCartItems() {
        itemsContainer.removeAllViews();

        for (CartItem item : cartItems) {
            View row = createItemRow(item);
            itemsContainer.addView(row);
        }
    }

    /** 创建单个商品行视图 */
    private View createItemRow(CartItem item) {
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

        String imageUrl = item.getImage_url();
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
        tvName.setText(item.getName());
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
        tvPrice.setText(String.format(Locale.getDefault(), "¥%.2f", item.getPrice()));
        tvPrice.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvPrice.setTextColor(Color.parseColor("#FF3B30"));
        tvPrice.setTypeface(null, Typeface.BOLD);
        row.addView(tvPrice);

        return row;
    }

    /** 更新合计信息 */
    private void updateSummary() {
        double total = 0;
        int count = 0;
        for (CartItem item : cartItems) {
            total += item.getPrice() * item.getQuantity();
            count += item.getQuantity();
        }
        tvCount.setText(String.format(Locale.getDefault(), "共%d件", count));
        tvTotal.setText(String.format(Locale.getDefault(), "¥%.2f", total));
    }

    /** 提交订单 */
    private void submitOrder() {
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String address = etAddress.getText().toString().trim();
        String remark = etRemark.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "请输入收货人", Toast.LENGTH_SHORT).show();
            etName.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(phone)) {
            Toast.makeText(this, "请输入手机号", Toast.LENGTH_SHORT).show();
            etPhone.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(address)) {
            Toast.makeText(this, "请输入收货地址", Toast.LENGTH_SHORT).show();
            etAddress.requestFocus();
            return;
        }

        // 构造请求体
        Map<String, Object> body = new HashMap<>();
        body.put("receiver_name", name);
        body.put("receiver_phone", phone);
        body.put("receiver_address", address);
        body.put("user_id", resolveUserId());
        if (!TextUtils.isEmpty(remark)) {
            body.put("remark", remark);
        }

        btnSubmit.setEnabled(false);
        btnSubmit.setText("提交中...");

        ApiClient.getInstance().post("/orders", body, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to create order", e);
                mainHandler.post(() -> {
                    btnSubmit.setEnabled(true);
                    btnSubmit.setText("提交订单");
                    Toast.makeText(OrderConfirmActivity.this,
                            "提交失败，请检查网络", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    String errBody = "";
                    try {
                        if (response.body() != null) errBody = response.body().string();
                    } catch (Exception ignored) {}
                    String finalErr = errBody;
                    mainHandler.post(() -> {
                        btnSubmit.setEnabled(true);
                        btnSubmit.setText("提交订单");
                        Toast.makeText(OrderConfirmActivity.this,
                                "提交失败 (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                try {
                    String json = response.body().string();
                    JsonObject obj = gson.fromJson(json, JsonObject.class);
                    String orderNo = "";
                    if (obj.has("order_no")) {
                        orderNo = obj.get("order_no").getAsString();
                    } else if (obj.has("data") && obj.get("data").isJsonObject()) {
                        JsonObject data = obj.getAsJsonObject("data");
                        if (data.has("order_no")) {
                            orderNo = data.get("order_no").getAsString();
                        }
                    }
                    String finalOrderNo = orderNo;
                    mainHandler.post(() -> {
                        btnSubmit.setEnabled(true);
                        btnSubmit.setText("提交订单");
                        showOrderSuccessDialog(finalOrderNo);
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing order response", e);
                    mainHandler.post(() -> {
                        btnSubmit.setEnabled(true);
                        btnSubmit.setText("提交订单");
                        Toast.makeText(OrderConfirmActivity.this,
                                "数据解析失败", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    /** 订单创建成功弹窗 */
    private void showOrderSuccessDialog(String orderNo) {
        new AlertDialog.Builder(this)
                .setTitle("订单创建成功！")
                .setMessage("订单号: " + orderNo + "\n是否立即支付？")
                .setPositiveButton("立即支付", (dialog, which) -> {
                    payOrder(orderNo);
                })
                .setNegativeButton("稍后支付", (dialog, which) -> {
                    Intent intent = new Intent(OrderConfirmActivity.this, OrderListActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    /** 支付订单 */
    private void payOrder(String orderNo) {
        int uid = resolveUserId();
        ApiClient.getInstance().put("/orders/" + orderNo + "/pay?user_id=" + uid, new HashMap<>(), new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to pay order", e);
                mainHandler.post(() ->
                        Toast.makeText(OrderConfirmActivity.this,
                                "支付请求失败", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                mainHandler.post(() -> {
                    if (response.isSuccessful()) {
                        Intent intent = new Intent(OrderConfirmActivity.this, OrderDetailActivity.class);
                        intent.putExtra("order_no", orderNo);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(OrderConfirmActivity.this,
                                "支付失败 (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private int resolveUserId() {
        int uid = ApiClient.getInstance().getUserId();
        if (uid <= 0) {
            SharedPreferences sp = getSharedPreferences("auth", MODE_PRIVATE);
            uid = sp.getInt("user_id", 1);
        }
        return uid > 0 ? uid : 1;
    }

    /** 解析购物车列表 */
    private List<CartItem> parseCartList(String json) {
        List<CartItem> items = new ArrayList<>();
        try {
            JsonElement root = gson.fromJson(json, JsonElement.class);
            JsonArray array = null;

            if (root.isJsonArray()) {
                array = root.getAsJsonArray();
            } else if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                if (obj.has("items") && obj.get("items").isJsonArray()) {
                    array = obj.getAsJsonArray("items");
                } else if (obj.has("data")) {
                    JsonElement data = obj.get("data");
                    if (data.isJsonArray()) {
                        array = data.getAsJsonArray();
                    } else if (data.isJsonObject()) {
                        JsonObject dataObj = data.getAsJsonObject();
                        if (dataObj.has("items") && dataObj.get("items").isJsonArray()) {
                            array = dataObj.getAsJsonArray("items");
                        }
                    }
                }
            }

            if (array != null) {
                for (JsonElement el : array) {
                    CartItem item = gson.fromJson(el, CartItem.class);
                    items.add(item);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "parseCartList error", e);
        }
        return items;
    }
}
