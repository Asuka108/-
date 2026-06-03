package com.airpods.assistant;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airpods.assistant.adapter.OrderAdapter;
import com.airpods.assistant.api.ApiClient;
import com.airpods.assistant.model.Order;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * 订单列表页面
 * 展示用户全部订单，支持按状态筛选
 */
public class OrderListActivity extends AppCompatActivity {

    private static final String TAG = "OrderListActivity";

    private RecyclerView rvOrders;
    private View emptyState;
    private TextView tabAll, tabPending, tabCompleted;

    private OrderAdapter adapter;
    private final List<Order> orders = new ArrayList<>();
    private final Gson gson = new Gson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String currentFilter = ""; // 空=全部, pending, completed

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_list);

        initViews();
        setupRecyclerView();
        loadOrders();
    }

    private void initViews() {
        rvOrders = findViewById(R.id.rv_orders);
        emptyState = findViewById(R.id.empty_state);
        tabAll = findViewById(R.id.tab_all);
        tabPending = findViewById(R.id.tab_pending);
        tabCompleted = findViewById(R.id.tab_completed);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        tabAll.setOnClickListener(v -> switchTab(0));
        tabPending.setOnClickListener(v -> switchTab(1));
        tabCompleted.setOnClickListener(v -> switchTab(2));
    }

    private void setupRecyclerView() {
        rvOrders.setLayoutManager(new LinearLayoutManager(this));
        adapter = new OrderAdapter();
        adapter.setOnOrderClickListener(order -> {
            Intent intent = new Intent(OrderListActivity.this, OrderDetailActivity.class);
            intent.putExtra("order_no", order.getOrder_no());
            startActivity(intent);
        });
        rvOrders.setAdapter(adapter);
    }

    /** 切换Tab */
    private void switchTab(int tabIndex) {
        // 重置所有Tab样式
        resetTabStyles();

        switch (tabIndex) {
            case 0:
                currentFilter = "";
                tabAll.setTextColor(Color.parseColor("#007AFF"));
                tabAll.setTypeface(null, Typeface.BOLD);
                break;
            case 1:
                currentFilter = "pending";
                tabPending.setTextColor(Color.parseColor("#007AFF"));
                tabPending.setTypeface(null, Typeface.BOLD);
                break;
            case 2:
                currentFilter = "completed";
                tabCompleted.setTextColor(Color.parseColor("#007AFF"));
                tabCompleted.setTypeface(null, Typeface.BOLD);
                break;
        }

        loadOrders();
    }

    /** 重置所有Tab样式为未选中 */
    private void resetTabStyles() {
        tabAll.setTextColor(Color.parseColor("#8E8E93"));
        tabAll.setTypeface(null, Typeface.NORMAL);
        tabPending.setTextColor(Color.parseColor("#8E8E93"));
        tabPending.setTypeface(null, Typeface.NORMAL);
        tabCompleted.setTextColor(Color.parseColor("#8E8E93"));
        tabCompleted.setTypeface(null, Typeface.NORMAL);
    }

    /** 加载订单列表 */
    private void loadOrders() {
        int uid = resolveUserId();
        Map<String, String> params = new HashMap<>();
        params.put("user_id", String.valueOf(uid));
        if (!currentFilter.isEmpty()) {
            params.put("status", currentFilter);
        }

        ApiClient.getInstance().get("/orders", params, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to load orders", e);
                mainHandler.post(() ->
                        Toast.makeText(OrderListActivity.this,
                                "加载订单失败", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    mainHandler.post(() ->
                            Toast.makeText(OrderListActivity.this,
                                    "加载失败 (" + response.code() + ")", Toast.LENGTH_SHORT).show());
                    return;
                }

                try {
                    String json = response.body().string();
                    List<Order> parsed = parseOrderList(json);
                    mainHandler.post(() -> {
                        orders.clear();
                        orders.addAll(parsed);
                        adapter.setOrders(orders);
                        updateEmptyState();
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing orders", e);
                    mainHandler.post(() ->
                            Toast.makeText(OrderListActivity.this,
                                    "数据解析失败", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private int resolveUserId() {
        int uid = ApiClient.getInstance().getUserId();
        if (uid <= 0) {
            android.content.SharedPreferences sp = getSharedPreferences("auth", MODE_PRIVATE);
            uid = sp.getInt("user_id", 1);
        }
        return uid > 0 ? uid : 1;
    }

    /** 更新空状态 */
    private void updateEmptyState() {
        if (orders.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            rvOrders.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            rvOrders.setVisibility(View.VISIBLE);
        }
    }

    /** 解析订单列表 */
    private List<Order> parseOrderList(String json) {
        List<Order> result = new ArrayList<>();
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
                    Order order = gson.fromJson(el, Order.class);
                    result.add(order);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "parseOrderList error", e);
        }
        return result;
    }
}
