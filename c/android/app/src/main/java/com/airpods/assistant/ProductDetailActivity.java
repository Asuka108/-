package com.airpods.assistant;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.airpods.assistant.api.ApiClient;
import com.airpods.assistant.model.Product;
import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * 商品详情页
 * 展示商品图片、价格、规格参数、产品特色，支持加入购物车和立即购买
 */
public class ProductDetailActivity extends AppCompatActivity {

    private static final String TAG = "ProductDetailActivity";

    private ImageView ivMainImage;
    private TextView tvPrice, tvOriginalPrice, tvSales, tvName, tvDescription;
    private LinearLayout specsContainer, featuresContainer;
    private ProgressBar progressBar;
    private TextView tvError;
    private ScrollView scrollContent;

    private int productId;
    private Product product;
    private final Gson gson = new Gson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_detail);

        productId = getIntent().getIntExtra("product_id", -1);
        if (productId <= 0) {
            Toast.makeText(this, "商品不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        loadProductDetail();
    }

    private void initViews() {
        ivMainImage = findViewById(R.id.iv_main_image);
        tvPrice = findViewById(R.id.tv_price);
        tvOriginalPrice = findViewById(R.id.tv_original_price);
        tvSales = findViewById(R.id.tv_sales);
        tvName = findViewById(R.id.tv_name);
        tvDescription = findViewById(R.id.tv_description);
        specsContainer = findViewById(R.id.specs_container);
        featuresContainer = findViewById(R.id.features_container);
        progressBar = findViewById(R.id.progress_bar);
        tvError = findViewById(R.id.tv_error);
        scrollContent = findViewById(R.id.scroll_content);

        // 返回按钮
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // 分享按钮（暂不实现）
        findViewById(R.id.btn_share).setOnClickListener(v ->
                Toast.makeText(this, "分享功能开发中", Toast.LENGTH_SHORT).show());

        // 加入购物车按钮样式（橙色）
        TextView btnAddCart = findViewById(R.id.btn_add_cart);
        android.graphics.drawable.GradientDrawable cartBg = new android.graphics.drawable.GradientDrawable();
        cartBg.setColor(Color.parseColor("#FF9500"));
        cartBg.setCornerRadius(dp(6));
        btnAddCart.setBackground(cartBg);
        btnAddCart.setOnClickListener(v -> addToCart(false));

        // 立即购买按钮样式（蓝色）
        TextView btnBuyNow = findViewById(R.id.btn_buy_now);
        android.graphics.drawable.GradientDrawable buyBg = new android.graphics.drawable.GradientDrawable();
        buyBg.setColor(Color.parseColor("#007AFF"));
        buyBg.setCornerRadius(dp(6));
        btnBuyNow.setBackground(buyBg);
        btnBuyNow.setOnClickListener(v -> addToCart(true));
    }

    /** 加载商品详情 */
    private void loadProductDetail() {
        showLoading(true);

        ApiClient.getInstance().get("/products/" + productId, null, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to load product detail", e);
                mainHandler.post(() -> {
                    showLoading(false);
                    showError("网络连接失败，请检查网络后重试");
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    mainHandler.post(() -> {
                        showLoading(false);
                        showError("加载失败 (" + response.code() + ")");
                    });
                    return;
                }

                try {
                    String json = response.body().string();
                    product = parseProductDetail(json);
                    mainHandler.post(() -> {
                        showLoading(false);
                        if (product != null) {
                            displayProduct();
                        } else {
                            showError("商品数据解析失败");
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing product detail", e);
                    mainHandler.post(() -> {
                        showLoading(false);
                        showError("数据解析失败");
                    });
                }
            }
        });
    }

    /** 解析商品详情 JSON，支持多种返回格式 */
    private Product parseProductDetail(String json) {
        try {
            JsonElement root = gson.fromJson(json, JsonElement.class);
            JsonObject productObj = null;

            if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                if (obj.has("data") && obj.get("data").isJsonObject()) {
                    productObj = obj.getAsJsonObject("data");
                } else if (obj.has("id")) {
                    productObj = obj;
                }
            }

            if (productObj != null) {
                return gson.fromJson(productObj, Product.class);
            }
        } catch (Exception e) {
            Log.e(TAG, "parseProductDetail error", e);
        }
        return null;
    }

    /** 展示商品信息 */
    private void displayProduct() {
        scrollContent.setVisibility(View.VISIBLE);

        // 主图
        String imageUrl = product.getImage_url();
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(this)
                    .load(imageUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .centerCrop()
                    .into(ivMainImage);
        }

        // 现价
        tvPrice.setText(String.format(Locale.getDefault(), "¥%.2f", product.getPrice()));

        // 原价（删除线）
        if (product.getOriginal_price() > 0 && product.getOriginal_price() > product.getPrice()) {
            tvOriginalPrice.setVisibility(View.VISIBLE);
            tvOriginalPrice.setText(String.format(Locale.getDefault(), "¥%.2f", product.getOriginal_price()));
            tvOriginalPrice.setPaintFlags(
                    tvOriginalPrice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            tvOriginalPrice.setVisibility(View.GONE);
        }

        // 销量
        tvSales.setText(String.format(Locale.getDefault(), "已售%d件", product.getSales()));

        // 商品名称
        tvName.setText(product.getName());

        // 商品描述
        if (product.getDescription() != null && !product.getDescription().isEmpty()) {
            tvDescription.setVisibility(View.VISIBLE);
            tvDescription.setText(product.getDescription());
        } else {
            tvDescription.setVisibility(View.GONE);
        }

        // 规格参数
        parseAndDisplaySpecs(product.getSpecs());

        // 产品特色
        parseAndDisplayFeatures(product.getFeatures());
    }

    /** 解析并展示规格参数 */
    private void parseAndDisplaySpecs(String specsJson) {
        specsContainer.removeAllViews();

        if (specsJson == null || specsJson.isEmpty() || "{}".equals(specsJson.trim())) {
            specsContainer.setVisibility(View.GONE);
            return;
        }

        try {
            // 支持两种格式：
            // 1. JSON对象: {"芯片":"Apple H1", "蓝牙":"5.0", ...}
            // 2. JSON数组: [{"key":"芯片","value":"Apple H1"}, ...]
            JsonElement root = gson.fromJson(specsJson, JsonElement.class);
            if (root.isJsonArray()) {
                // 数组格式
                JsonArray specsArray = root.getAsJsonArray();
                specsContainer.setVisibility(View.VISIBLE);
                for (int i = 0; i < specsArray.size(); i++) {
                    JsonObject spec = specsArray.get(i).getAsJsonObject();
                    String key = spec.has("key") ? spec.get("key").getAsString() : "";
                    String value = spec.has("value") ? spec.get("value").getAsString() : "";
                    if (!key.isEmpty()) {
                        View row = createSpecRow(key, value, i % 2 == 0);
                        specsContainer.addView(row);
                    }
                }
            } else if (root.isJsonObject()) {
                // 对象格式: 逐个key-value
                JsonObject specsObj = root.getAsJsonObject();
                if (specsObj.size() == 0) {
                    specsContainer.setVisibility(View.GONE);
                    return;
                }
                specsContainer.setVisibility(View.VISIBLE);
                int i = 0;
                for (String key : specsObj.keySet()) {
                    String value = specsObj.get(key).getAsString();
                    View row = createSpecRow(key, value, i % 2 == 0);
                    specsContainer.addView(row);
                    i++;
                }
            } else {
                specsContainer.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Log.e(TAG, "parseAndDisplaySpecs error", e);
            specsContainer.setVisibility(View.GONE);
        }
    }

    /** 创建规格参数行 */
    private View createSpecRow(String key, String value, boolean evenRow) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackgroundColor(evenRow ? Color.parseColor("#F8F8FA") : Color.WHITE);

        TextView tvKey = new TextView(this);
        tvKey.setText(key);
        tvKey.setTextSize(14);
        tvKey.setTextColor(Color.parseColor("#999999"));
        tvKey.setWidth(dp(100));
        tvKey.setTypeface(null, Typeface.NORMAL);
        row.addView(tvKey);

        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextSize(14);
        tvValue.setTextColor(Color.parseColor("#333333"));
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tvValue.setLayoutParams(valueParams);
        row.addView(tvValue);

        return row;
    }

    /** 解析并展示产品特色 */
    private void parseAndDisplayFeatures(String featuresJson) {
        featuresContainer.removeAllViews();

        if (featuresJson == null || featuresJson.isEmpty()) {
            featuresContainer.setVisibility(View.GONE);
            return;
        }

        try {
            JsonArray featuresArray = gson.fromJson(featuresJson, JsonArray.class);
            if (featuresArray == null || featuresArray.size() == 0) {
                featuresContainer.setVisibility(View.GONE);
                return;
            }

            featuresContainer.setVisibility(View.VISIBLE);
            for (int i = 0; i < featuresArray.size(); i++) {
                String feature = featuresArray.get(i).getAsString();
                TextView tvFeature = createFeatureItem(feature);
                featuresContainer.addView(tvFeature);
            }
        } catch (Exception e) {
            Log.e(TAG, "parseAndDisplayFeatures error", e);
            featuresContainer.setVisibility(View.GONE);
        }
    }

    /** 创建单个产品特色项 */
    private TextView createFeatureItem(String text) {
        TextView tv = new TextView(this);
        tv.setText("•  " + text);
        tv.setTextSize(14);
        tv.setTextColor(Color.parseColor("#666666"));
        tv.setLineSpacing(0, 1.3f);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        int marginBottom = dp(8);
        params.bottomMargin = marginBottom;
        tv.setLayoutParams(params);

        return tv;
    }

    /** 加入购物车 / 立即购买 */
    private void addToCart(boolean buyNow) {
        if (product == null) return;

        int uid = ApiClient.getInstance().getUserId();
        if (uid <= 0) {
            SharedPreferences sp = getSharedPreferences("auth", MODE_PRIVATE);
            uid = sp.getInt("user_id", 1);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("product_id", product.getId());
        body.put("quantity", 1);
        body.put("user_id", uid);

        ApiClient.getInstance().post("/cart", body, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Add to cart failed", e);
                mainHandler.post(() ->
                        Toast.makeText(ProductDetailActivity.this,
                                "操作失败，请检查网络", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                mainHandler.post(() -> {
                    if (response.isSuccessful()) {
                        if (buyNow) {
                            // 立即购买：跳转订单确认页
                            try {
                                startActivity(new android.content.Intent(
                                        ProductDetailActivity.this, OrderConfirmActivity.class));
                            } catch (Exception e) {
                                Log.e(TAG, "OrderConfirmActivity not found", e);
                                Toast.makeText(ProductDetailActivity.this,
                                        "已加入购物车", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(ProductDetailActivity.this,
                                    "已加入购物车", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(ProductDetailActivity.this,
                                "操作失败 (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        scrollContent.setVisibility(show ? View.GONE : View.VISIBLE);
        tvError.setVisibility(View.GONE);
    }

    private void showError(String message) {
        tvError.setVisibility(View.VISIBLE);
        tvError.setText(message);
        scrollContent.setVisibility(View.GONE);
    }

    /** dp 转 px */
    private int dp(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
