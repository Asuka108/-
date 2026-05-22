package com.airpods.assistant;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airpods.assistant.api.ApiClient;
import com.airpods.assistant.model.KnowledgeItem;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * 管理员后台 - 知识库管理
 */
public class AdminActivity extends AppCompatActivity {

    private RecyclerView rvKnowledge;
    private KnowledgeAdapter adapter;
    private List<KnowledgeItem> allItems = new ArrayList<>();
    private List<KnowledgeItem> filteredItems = new ArrayList<>();

    private Gson gson = new Gson();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private String[] categories = {"全部", "配对连接", "充电续航", "音质降噪",
            "操作使用", "保修售后", "真假鉴别", "产品对比"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        rvKnowledge = findViewById(R.id.rv_knowledge);
        rvKnowledge.setLayoutManager(new LinearLayoutManager(this));
        adapter = new KnowledgeAdapter();
        rvKnowledge.setAdapter(adapter);

        // 分类筛选
        Spinner spinner = findViewById(R.id.spinner_category);
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, categories);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                filterItems(categories[position]);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 新增按钮
        findViewById(R.id.btn_add).setOnClickListener(v -> showEditDialog(null));

        // 搜索
        EditText etSearch = findViewById(R.id.et_search);
        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void afterTextChanged(android.text.Editable s) { applySearch(s.toString()); }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        loadData();
    }

    private void loadData() {
        new Thread(() -> {
            try {
                String resp = ApiClient.getInstance().getSync("/knowledge/search?q=");
                JsonObject json = gson.fromJson(resp, JsonObject.class);
                JsonArray arr = json.has("items") ? json.getAsJsonArray("items")
                        : json.has("data") ? json.getAsJsonArray("data") : new JsonArray();
                allItems = gson.fromJson(arr, new TypeToken<List<KnowledgeItem>>(){}.getType());
                mainHandler.post(() -> filterItems("全部"));
            } catch (Exception e) {
                mainHandler.post(() ->
                        Toast.makeText(this, "加载数据失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void filterItems(String category) {
        filteredItems.clear();
        for (KnowledgeItem item : allItems) {
            if ("全部".equals(category) || category.equals(item.getCategory())) {
                filteredItems.add(item);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void applySearch(String keyword) {
        if (TextUtils.isEmpty(keyword)) {
            filterItems("全部");
            return;
        }
        filteredItems.clear();
        String kw = keyword.toLowerCase();
        for (KnowledgeItem item : allItems) {
            if (item.getQuestion().toLowerCase().contains(kw)
                    || item.getKeywords().toLowerCase().contains(kw)) {
                filteredItems.add(item);
            }
        }
        adapter.notifyDataSetChanged();
    }

    /** 新增/编辑弹窗 */
    private void showEditDialog(KnowledgeItem editItem) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_knowledge, null);
        builder.setView(dialogView);

        Spinner spCat = dialogView.findViewById(R.id.sp_cat);
        EditText etQ = dialogView.findViewById(R.id.et_question);
        EditText etA = dialogView.findViewById(R.id.et_answer);
        EditText etK = dialogView.findViewById(R.id.et_keywords);

        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"配对连接","充电续航","音质降噪","操作使用","保修售后","真假鉴别","产品对比"});
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCat.setAdapter(catAdapter);

        boolean isEdit = editItem != null;
        builder.setTitle(isEdit ? "编辑知识条目" : "新增知识条目");

        if (isEdit) {
            for (int i = 0; i < categories.length - 1; i++) {
                if (categories[i+1].equals(editItem.getCategory())) {
                    spCat.setSelection(i);
                    break;
                }
            }
            etQ.setText(editItem.getQuestion());
            etA.setText(editItem.getAnswer());
            etK.setText(editItem.getKeywords());
        }

        builder.setPositiveButton("保存", (dialog, which) -> {
            KnowledgeItem item = new KnowledgeItem();
            item.setCategory(spCat.getSelectedItem().toString());
            item.setQuestion(etQ.getText().toString().trim());
            item.setAnswer(etA.getText().toString().trim());
            item.setKeywords(etK.getText().toString().trim());

            if (TextUtils.isEmpty(item.getQuestion()) || TextUtils.isEmpty(item.getAnswer())) {
                Toast.makeText(this, "问题和回答不能为空", Toast.LENGTH_SHORT).show();
                return;
            }

            if (isEdit) {
                item.setId(editItem.getId());
                updateItem(item);
            } else {
                addItem(item);
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void addItem(KnowledgeItem item) {
        ApiClient.getInstance().post("/knowledge/items", item, new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> Toast.makeText(AdminActivity.this, "新增失败", Toast.LENGTH_SHORT).show());
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                mainHandler.post(() -> {
                    Toast.makeText(AdminActivity.this, "新增成功", Toast.LENGTH_SHORT).show();
                    loadData();
                });
            }
        });
    }

    private void updateItem(KnowledgeItem item) {
        ApiClient.getInstance().put("/knowledge/items/" + item.getId(), item, new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> Toast.makeText(AdminActivity.this, "修改失败", Toast.LENGTH_SHORT).show());
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                mainHandler.post(() -> {
                    Toast.makeText(AdminActivity.this, "修改成功", Toast.LENGTH_SHORT).show();
                    loadData();
                });
            }
        });
    }

    private void deleteItem(int id) {
        ApiClient.getInstance().delete("/knowledge/items/" + id, new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> Toast.makeText(AdminActivity.this, "删除失败", Toast.LENGTH_SHORT).show());
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                mainHandler.post(() -> {
                    Toast.makeText(AdminActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                    loadData();
                });
            }
        });
    }

    // ===== RecyclerView Adapter =====
    class KnowledgeAdapter extends RecyclerView.Adapter<KnowledgeAdapter.VH> {
        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_knowledge, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            KnowledgeItem item = filteredItems.get(position);
            holder.tvCat.setText(item.getCategory());
            holder.tvQ.setText(item.getQuestion());
            holder.tvA.setText(item.getAnswer());
            holder.tvKeywords.setText("关键词: " + item.getKeywords());
            holder.btnEdit.setOnClickListener(v -> showEditDialog(item));
            holder.btnDelete.setOnClickListener(v -> new AlertDialog.Builder(AdminActivity.this)
                    .setTitle("确认删除？")
                    .setMessage("删除后无法恢复")
                    .setPositiveButton("删除", (d, w) -> deleteItem(item.getId()))
                    .setNegativeButton("取消", null)
                    .show());
        }

        @Override
        public int getItemCount() { return filteredItems.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvCat, tvQ, tvA, tvKeywords;
            View btnEdit, btnDelete;
            VH(View v) {
                super(v);
                tvCat = v.findViewById(R.id.tv_kb_cat);
                tvQ = v.findViewById(R.id.tv_kb_q);
                tvA = v.findViewById(R.id.tv_kb_a);
                tvKeywords = v.findViewById(R.id.tv_kb_kw);
                btnEdit = v.findViewById(R.id.btn_kb_edit);
                btnDelete = v.findViewById(R.id.btn_kb_del);
            }
        }
    }
}
