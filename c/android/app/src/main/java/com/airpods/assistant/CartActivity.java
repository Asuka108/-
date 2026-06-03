package com.airpods.assistant;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airpods.assistant.adapter.CartAdapter;
import com.airpods.assistant.api.ApiClient;
import com.airpods.assistant.model.CartItem;
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
 * 购物车页面
 * 展示购物车商品列表，支持全选、数量调整、删除和结算
 */
public class CartActivity extends AppCompatActivity {

    private static final String TAG = "CartActivity";

    private RecyclerView rvCart;
    private View emptyView;
    private View bottomBar;
    private CheckBox cbSelectAll;
    private TextView tvTotal;
    private TextView btnCheckout;

    private CartAdapter adapter;
    private final List<CartItem> cartItems = new ArrayList<>();
    private final Gson gson = new Gson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);

        initViews();
        setupRecyclerView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCart();
    }

    private void initViews() {
        rvCart = findViewById(R.id.rv_cart);
        emptyView = findViewById(R.id.empty_view);
        bottomBar = findViewById(R.id.bottom_bar);
        cbSelectAll = findViewById(R.id.cb_select_all);
        tvTotal = findViewById(R.id.tv_total);
        btnCheckout = findViewById(R.id.btn_checkout);

        // 返回按钮
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // 全选
        cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (CartItem item : cartItems) {
                item.setSelected(isChecked);
            }
            adapter.notifyDataSetChanged();
            calculateTotal();
        });

        // 去结算
        btnCheckout.setOnClickListener(v -> {
            boolean hasSelected = false;
            for (CartItem item : cartItems) {
                if (item.isSelected()) {
                    hasSelected = true;
                    break;
                }
            }
            if (!hasSelected) {
                Toast.makeText(this, "请先选择商品", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, OrderConfirmActivity.class);
            startActivity(intent);
        });
    }

    private void setupRecyclerView() {
        rvCart.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CartAdapter();
        adapter.setOnCartItemChangeListener(new CartAdapter.OnCartItemChangeListener() {
            @Override
            public void onSelectChange(CartItem item, boolean selected) {
                updateSelectAllState();
                calculateTotal();
            }

            @Override
            public void onQuantityChange(CartItem item, int newQuantity) {
                updateCartItem(item.getId(), newQuantity);
            }

            @Override
            public void onDelete(CartItem item) {
                deleteCartItem(item.getId());
            }
        });
        rvCart.setAdapter(adapter);
    }

    /** 加载购物车数据 */
    private void loadCart() {
        int uid = resolveUserId();
        Map<String, String> params = new HashMap<>();
        params.put("user_id", String.valueOf(uid));
        ApiClient.getInstance().get("/cart", params, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to load cart", e);
                mainHandler.post(() ->
                        Toast.makeText(CartActivity.this, "加载购物车失败", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    mainHandler.post(() ->
                            Toast.makeText(CartActivity.this,
                                    "加载失败 (" + response.code() + ")", Toast.LENGTH_SHORT).show());
                    return;
                }

                try {
                    String json = response.body().string();
                    List<CartItem> parsed = parseCartList(json);
                    mainHandler.post(() -> {
                        cartItems.clear();
                        cartItems.addAll(parsed);
                        adapter.setItems(cartItems);
                        updateEmptyState();
                        updateSelectAllState();
                        calculateTotal();
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing cart", e);
                    mainHandler.post(() ->
                            Toast.makeText(CartActivity.this, "数据解析失败", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    /** 更新购物车商品数量 */
    private void updateCartItem(int cartItemId, int newQuantity) {
        Map<String, Object> body = new HashMap<>();
        body.put("quantity", newQuantity);
        int uid = resolveUserId();

        ApiClient.getInstance().put("/cart/" + cartItemId + "?user_id=" + uid, body, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to update cart item", e);
                mainHandler.post(() ->
                        Toast.makeText(CartActivity.this, "更新失败", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                mainHandler.post(() -> {
                    if (response.isSuccessful()) {
                        loadCart();
                    } else {
                        Toast.makeText(CartActivity.this,
                                "更新失败 (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    /** 删除购物车商品 */
    private void deleteCartItem(int cartItemId) {
        int uid = resolveUserId();
        ApiClient.getInstance().delete("/cart/" + cartItemId + "?user_id=" + uid, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to delete cart item", e);
                mainHandler.post(() ->
                        Toast.makeText(CartActivity.this, "删除失败", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                mainHandler.post(() -> {
                    if (response.isSuccessful()) {
                        // 从列表中移除
                        for (int i = 0; i < cartItems.size(); i++) {
                            if (cartItems.get(i).getId() == cartItemId) {
                                cartItems.remove(i);
                                break;
                            }
                        }
                        adapter.setItems(cartItems);
                        updateEmptyState();
                        updateSelectAllState();
                        calculateTotal();
                    } else {
                        Toast.makeText(CartActivity.this,
                                "删除失败 (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    /** 计算选中商品总价 */
    private void calculateTotal() {
        double total = 0;
        int selectedCount = 0;
        for (CartItem item : cartItems) {
            if (item.isSelected()) {
                total += item.getPrice() * item.getQuantity();
                selectedCount++;
            }
        }
        tvTotal.setText(String.format(Locale.getDefault(), "¥%.2f", total));
        btnCheckout.setText(String.format(Locale.getDefault(), "去结算(%d)", selectedCount));
    }

    /** 更新全选框状态 */
    private void updateSelectAllState() {
        if (cartItems.isEmpty()) {
            cbSelectAll.setChecked(false);
            return;
        }
        boolean allSelected = true;
        for (CartItem item : cartItems) {
            if (!item.isSelected()) {
                allSelected = false;
                break;
            }
        }
        cbSelectAll.setOnCheckedChangeListener(null);
        cbSelectAll.setChecked(allSelected);
        cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (CartItem item : cartItems) {
                item.setSelected(isChecked);
            }
            adapter.notifyDataSetChanged();
            calculateTotal();
        });
    }

    /** 更新空状态视图 */
    private void updateEmptyState() {
        if (cartItems.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            rvCart.setVisibility(View.GONE);
            bottomBar.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            rvCart.setVisibility(View.VISIBLE);
            bottomBar.setVisibility(View.VISIBLE);
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

    /**
     * 解析购物车列表
     * 支持格式: {"items":[...]}、{"data":[...]}、{"data":{"items":[...]}}、裸数组
     */
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
                    item.setSelected(true); // 默认选中
                    items.add(item);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "parseCartList error", e);
        }
        return items;
    }
}
