package com.airpods.assistant;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
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
 * 商城 Fragment
 * 展示商品分类和网格商品列表，支持搜索和分类筛选
 */
public class ShopFragment extends Fragment {

    private static final String TAG = "ShopFragment";

    private RecyclerView rvProducts;
    private EditText etSearch;
    private LinearLayout categoryContainer;
    private ProductAdapter productAdapter;

    private String currentCategory = "";
    private int selectedCategoryIndex = 0;
    private final List<String> categories = new ArrayList<>();

    private final Gson gson = new Gson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;
    private static final long SEARCH_DEBOUNCE_MS = 500;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_shop, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupRecyclerView();
        setupSearch();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadCategories();
        loadProducts();
    }

    private void initViews(View view) {
        rvProducts = view.findViewById(R.id.rv_products);
        etSearch = view.findViewById(R.id.et_search);
        categoryContainer = view.findViewById(R.id.category_container);

        // 购物车按钮
        view.findViewById(R.id.btn_cart).setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), CartActivity.class);
            startActivity(intent);
        });
    }

    private void setupRecyclerView() {
        GridLayoutManager gridLayout = new GridLayoutManager(requireContext(), 2);
        rvProducts.setLayoutManager(gridLayout);
        productAdapter = new ProductAdapter();
        productAdapter.setOnItemClickListener(product -> {
            Intent intent = new Intent(requireContext(), ProductDetailActivity.class);
            intent.putExtra("product_id", product.getId());
            startActivity(intent);
        });
        rvProducts.setAdapter(productAdapter);
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                // 防抖：延迟搜索
                if (searchRunnable != null) {
                    mainHandler.removeCallbacks(searchRunnable);
                }
                searchRunnable = () -> {
                    String keyword = s.toString().trim();
                    searchProducts(keyword);
                };
                mainHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS);
            }
        });
    }

    /** 加载分类列表 */
    private void loadCategories() {
        ApiClient.getInstance().get("/products/categories", null, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to load categories", e);
                // 分类加载失败时至少显示"全部"
                mainHandler.post(() -> {
                    if (isAdded()) {
                        categories.clear();
                        categories.add("全部");
                        buildCategoryChips();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    mainHandler.post(() -> {
                        if (isAdded()) {
                            categories.clear();
                            categories.add("全部");
                            buildCategoryChips();
                        }
                    });
                    return;
                }

                try {
                    String json = response.body().string();
                    List<String> parsed = parseCategoryList(json);
                    mainHandler.post(() -> {
                        if (isAdded()) {
                            categories.clear();
                            categories.add("全部");
                            categories.addAll(parsed);
                            buildCategoryChips();
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing categories", e);
                    mainHandler.post(() -> {
                        if (isAdded()) {
                            categories.clear();
                            categories.add("全部");
                            buildCategoryChips();
                        }
                    });
                }
            }
        });
    }

    /** 解析分类列表 */
    private List<String> parseCategoryList(String json) {
        List<String> result = new ArrayList<>();
        try {
            JsonElement root = gson.fromJson(json, JsonElement.class);
            JsonArray array = null;

            if (root.isJsonArray()) {
                array = root.getAsJsonArray();
            } else if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                if (obj.has("data")) {
                    JsonElement data = obj.get("data");
                    if (data.isJsonArray()) {
                        array = data.getAsJsonArray();
                    } else if (data.isJsonObject() && data.getAsJsonObject().has("categories")) {
                        array = data.getAsJsonObject().getAsJsonArray("categories");
                    }
                } else if (obj.has("categories")) {
                    array = obj.getAsJsonArray("categories");
                }
            }

            if (array != null) {
                for (JsonElement el : array) {
                    if (el.isJsonPrimitive()) {
                        result.add(el.getAsString());
                    } else if (el.isJsonObject()) {
                        JsonObject catObj = el.getAsJsonObject();
                        if (catObj.has("name")) {
                            result.add(catObj.get("name").getAsString());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "parseCategoryList error", e);
        }
        return result;
    }

    /** 动态创建分类标签 */
    private void buildCategoryChips() {
        categoryContainer.removeAllViews();

        for (int i = 0; i < categories.size(); i++) {
            TextView chip = createCategoryChip(categories.get(i), i);
            categoryContainer.addView(chip);
        }
    }

    /** 创建单个分类标签 */
    private TextView createCategoryChip(String text, int index) {
        TextView chip = new TextView(requireContext());
        chip.setText(text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        chip.setGravity(Gravity.CENTER);
        chip.setMaxLines(1);

        int paddingHorizontal = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        int paddingVertical = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        chip.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        int marginEnd = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        params.setMarginEnd(marginEnd);
        chip.setLayoutParams(params);

        // 设置选中状态样式
        updateChipStyle(chip, index == selectedCategoryIndex);

        chip.setOnClickListener(v -> {
            selectedCategoryIndex = index;
            currentCategory = index == 0 ? "" : categories.get(index);

            // 更新所有标签样式
            for (int i = 0; i < categoryContainer.getChildCount(); i++) {
                View child = categoryContainer.getChildAt(i);
                if (child instanceof TextView) {
                    updateChipStyle((TextView) child, i == selectedCategoryIndex);
                }
            }

            // 清空搜索框并加载对应分类商品
            etSearch.setText("");
            loadProducts();
        });

        return chip;
    }

    /** 更新标签选中/未选中样式 */
    private void updateChipStyle(TextView chip, boolean selected) {
        if (selected) {
            chip.setTextColor(Color.WHITE);
            chip.setBackgroundColor(Color.parseColor("#007AFF"));
            chip.setTypeface(null, Typeface.BOLD);
            // 圆角背景
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(Color.parseColor("#007AFF"));
            bg.setCornerRadius(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 18, getResources().getDisplayMetrics()));
            chip.setBackground(bg);
        } else {
            chip.setTextColor(Color.parseColor("#007AFF"));
            chip.setTypeface(null, Typeface.NORMAL);
            // 圆角描边背景
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(Color.parseColor("#F2F2F7"));
            bg.setStroke((int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics()),
                    Color.parseColor("#007AFF"));
            bg.setCornerRadius(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 18, getResources().getDisplayMetrics()));
            chip.setBackground(bg);
        }
    }

    /** 搜索商品（防抖触发） */
    private void searchProducts(String keyword) {
        if (TextUtils.isEmpty(keyword)) {
            // 搜索框为空时恢复分类筛选
            loadProducts();
            return;
        }

        Map<String, String> params = new HashMap<>();
        params.put("q", keyword);
        params.put("page_size", "200");

        ApiClient.getInstance().get("/products", params, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Search failed", e);
                mainHandler.post(() -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(),
                                "搜索失败，请检查网络", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    mainHandler.post(() -> {
                        if (isAdded()) {
                            Toast.makeText(requireContext(),
                                    "搜索失败 (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }

                try {
                    String json = response.body().string();
                    List<Product> products = parseProductList(json);
                    mainHandler.post(() -> {
                        if (isAdded()) {
                            productAdapter.setProducts(products);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing search results", e);
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

    /** 加载商品列表 */
    private void loadProducts() {
        Map<String, String> params = new HashMap<>();
        params.put("page_size", "200");
        params.put("sort", "default");

        if (!TextUtils.isEmpty(currentCategory)) {
            params.put("category", currentCategory);
        }

        ApiClient.getInstance().get("/products", params, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to load products", e);
                mainHandler.post(() -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(),
                                "商品加载失败，请检查网络", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    mainHandler.post(() -> {
                        if (isAdded()) {
                            Toast.makeText(requireContext(),
                                    "商品加载失败 (" + response.code() + ")",
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
                            productAdapter.setProducts(products);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing products", e);
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
     * 解析商品列表
     * 支持格式: {"items":[...]}、{"data":[...]}、{"data":{"items":[...]}}、裸数组
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
}
