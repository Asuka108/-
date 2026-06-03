package com.airpods.assistant;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
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
 * 购物车 Fragment — 底部Tab，复用 adapter.CartAdapter
 */
public class CartFragment extends Fragment {

    private RecyclerView rvCart;
    private CartAdapter adapter;
    private TextView tvTotal, tvCheckout;
    private CheckBox cbSelectAll;
    private View layoutEmpty;

    private final List<CartItem> cartItems = new ArrayList<>();
    private final Gson gson = new Gson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_cart, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvCart = view.findViewById(R.id.rv_cart);
        tvTotal = view.findViewById(R.id.tv_total);
        tvCheckout = view.findViewById(R.id.tv_checkout);
        cbSelectAll = view.findViewById(R.id.cb_select_all);
        layoutEmpty = view.findViewById(R.id.layout_empty);

        rvCart.setLayoutManager(new LinearLayoutManager(getContext()));
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

        cbSelectAll.setOnCheckedChangeListener((btn, checked) -> {
            for (CartItem item : cartItems) {
                item.setSelected(checked);
            }
            adapter.notifyDataSetChanged();
            calculateTotal();
        });

        tvCheckout.setOnClickListener(v -> checkout());
    }

    @Override
    public void onResume() {
        super.onResume();
        loadCart();
    }

    private void loadCart() {
        int uid = resolveUserId();
        Map<String, String> params = new HashMap<>();
        params.put("user_id", String.valueOf(uid));
        ApiClient.getInstance().get("/cart", params, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> Toast.makeText(getContext(),
                        "加载购物车失败", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) return;
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
                } catch (Exception ignored) {}
            }
        });
    }

    private void calculateTotal() {
        double total = 0;
        int count = 0;
        for (CartItem item : cartItems) {
            if (item.isSelected()) {
                total += item.getPrice() * item.getQuantity();
                count++;
            }
        }
        tvTotal.setText(String.format(Locale.getDefault(), "¥%.2f", total));
        tvCheckout.setText(String.format(Locale.getDefault(), "结算(%d)", count));
    }

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
        cbSelectAll.setOnCheckedChangeListener((btn, checked) -> {
            for (CartItem item : cartItems) {
                item.setSelected(checked);
            }
            adapter.notifyDataSetChanged();
            calculateTotal();
        });
    }

    private void updateEmptyState() {
        if (cartItems.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            rvCart.setVisibility(View.GONE);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            rvCart.setVisibility(View.VISIBLE);
        }
    }

    private void updateCartItem(int cartItemId, int newQuantity) {
        Map<String, Object> body = new HashMap<>();
        body.put("quantity", newQuantity);
        int uid = resolveUserId();
        ApiClient.getInstance().put("/cart/" + cartItemId + "?user_id=" + uid, body, new Callback() {
            @Override public void onFailure(Call c, IOException e) {}
            @Override public void onResponse(Call c, Response r) {
                r.close();
                mainHandler.post(() -> loadCart());
            }
        });
    }

    private void deleteCartItem(int cartItemId) {
        int uid = resolveUserId();
        ApiClient.getInstance().delete("/cart/" + cartItemId + "?user_id=" + uid, new Callback() {
            @Override public void onFailure(Call c, IOException e) {}
            @Override public void onResponse(Call c, Response r) {
                r.close();
                mainHandler.post(() -> loadCart());
            }
        });
    }

    /** 解析购物车列表，支持数组和对象两种格式 */
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
        } catch (Exception ignored) {}
        return items;
    }

    private int resolveUserId() {
        int uid = ApiClient.getInstance().getUserId();
        if (uid <= 0 && getActivity() != null) {
            SharedPreferences sp = getActivity().getSharedPreferences("auth", 0);
            uid = sp.getInt("user_id", 1);
        }
        return uid > 0 ? uid : 1;
    }

    private void checkout() {
        boolean hasSelected = false;
        for (CartItem item : cartItems) {
            if (item.isSelected()) { hasSelected = true; break; }
        }
        if (!hasSelected) {
            Toast.makeText(getContext(), "请选择商品", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(getActivity(), OrderConfirmActivity.class);
        startActivity(intent);
    }
}
