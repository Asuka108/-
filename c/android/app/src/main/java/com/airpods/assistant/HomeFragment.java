package com.airpods.assistant;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airpods.assistant.adapter.ProductAdapter;
import com.airpods.assistant.api.ApiClient;
import com.airpods.assistant.model.Product;
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
 * 首页 Fragment
 * 展示热销推荐和新品上架两个水平商品列表
 */
public class HomeFragment extends Fragment implements ProductAdapter.OnItemClickListener {

    private static final String TAG = "HomeFragment";

    private RecyclerView rvHot;
    private RecyclerView rvNew;
    private ProductAdapter hotAdapter;
    private ProductAdapter newAdapter;

    private final Gson gson = new Gson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupRecyclerViews();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadHotProducts();
        loadNewProducts();
    }

    private void initViews(View view) {
        rvHot = view.findViewById(R.id.rv_hot);
        rvNew = view.findViewById(R.id.rv_new);

        // Cart button
        view.findViewById(R.id.btn_cart).setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), CartActivity.class);
            startActivity(intent);
        });
    }

    private void setupRecyclerViews() {
        // Hot products RecyclerView
        LinearLayoutManager hotLayout = new LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false);
        rvHot.setLayoutManager(hotLayout);
        hotAdapter = new ProductAdapter();
        hotAdapter.setOnItemClickListener(this);
        rvHot.setAdapter(hotAdapter);

        // New products RecyclerView
        LinearLayoutManager newLayout = new LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false);
        rvNew.setLayoutManager(newLayout);
        newAdapter = new ProductAdapter();
        newAdapter.setOnItemClickListener(this);
        rvNew.setAdapter(newAdapter);
    }

    /** Fetch hot products: sorted by sales descending */
    private void loadHotProducts() {
        Map<String, String> params = new HashMap<>();
        params.put("sort", "sales");
        params.put("page_size", "5");

        ApiClient.getInstance().get("/products", params, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to load hot products", e);
                mainHandler.post(() -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(),
                                "热销商品加载失败，请检查网络", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    mainHandler.post(() -> {
                        if (isAdded()) {
                            Toast.makeText(requireContext(),
                                    "热销商品加载失败 (" + response.code() + ")",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }

                try {
                    String json = response.body().string();
                    List<Product> products = parseProductList(json);
                    mainHandler.post(() -> {
                        if (isAdded()) {
                            hotAdapter.setProducts(products);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing hot products", e);
                    mainHandler.post(() -> {
                        if (isAdded()) {
                            Toast.makeText(requireContext(),
                                    "数据解析失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    /** Fetch new products: sorted by newest */
    private void loadNewProducts() {
        Map<String, String> params = new HashMap<>();
        params.put("sort", "newest");
        params.put("page_size", "5");

        ApiClient.getInstance().get("/products", params, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to load new products", e);
                mainHandler.post(() -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(),
                                "新品加载失败，请检查网络", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    mainHandler.post(() -> {
                        if (isAdded()) {
                            Toast.makeText(requireContext(),
                                    "新品加载失败 (" + response.code() + ")",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }

                try {
                    String json = response.body().string();
                    List<Product> products = parseProductList(json);
                    mainHandler.post(() -> {
                        if (isAdded()) {
                            newAdapter.setProducts(products);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing new products", e);
                    mainHandler.post(() -> {
                        if (isAdded()) {
                            Toast.makeText(requireContext(),
                                    "数据解析失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    /**
     * Parse product list from API response.
     * Supports formats: {"items":[...]}, {"data":[...]}, {"data":{"items":[...]}}, or bare array.
     */
    private List<Product> parseProductList(String json) {
        List<Product> products = new ArrayList<>();
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
                    Product product = gson.fromJson(el, Product.class);
                    products.add(product);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "parseProductList error", e);
        }
        return products;
    }

    @Override
    public void onItemClick(Product product) {
        Intent intent = new Intent(requireContext(), ProductDetailActivity.class);
        intent.putExtra("product_id", product.getId());
        startActivity(intent);
    }
}
