package com.airpods.assistant;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airpods.assistant.api.ApiClient;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * 对话历史页
 * 展示当前用户的所有对话记录，点击可查看详情
 */
public class HistoryActivity extends AppCompatActivity {

    private static final String TAG = "HistoryActivity";

    private RecyclerView rvHistory;
    private TextView tvEmpty;
    private HistoryAdapter adapter;
    private List<HistoryItem> items = new ArrayList<>();
    private Gson gson = new Gson();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        rvHistory = findViewById(R.id.rv_history);
        tvEmpty = findViewById(R.id.tv_empty);
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter();
        rvHistory.setAdapter(adapter);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        View btnClearAll = findViewById(R.id.btn_clear_all);
        btnClearAll.setOnClickListener(v -> clearAll());

        loadHistory();
    }

    /** 解析用户ID，优先从ApiClient获取，其次SharedPreferences，最后通过token接口查询 */
    private int resolveUserId() {
        ApiClient api = ApiClient.getInstance();
        int userId = api.getUserId();
        Log.d(TAG, "resolveUserId: ApiClient.userId=" + userId);
        if (userId > 0) return userId;

        SharedPreferences sp = getSharedPreferences("auth", MODE_PRIVATE);
        userId = sp.getInt("user_id", 0);
        Log.d(TAG, "resolveUserId: SharedPreferences.user_id=" + userId);
        if (userId > 0) {
            api.setUserId(userId);
            return userId;
        }

        // 有token但没有user_id → 通过token查询用户信息
        String token = api.getToken();
        if (token == null || token.isEmpty()) {
            token = sp.getString("token", null);
            if (token != null && !token.isEmpty()) {
                api.setToken(token);
            }
        }
        Log.d(TAG, "resolveUserId: token=" + (token != null ? token.substring(0, Math.min(8, token.length())) + "..." : "null"));
        if (token != null && !token.isEmpty()) {
            try {
                String resp = api.getSync("/auth/user/info?token=" + token);
                Log.d(TAG, "resolveUserId: /auth/user/info response=" + resp);
                JsonObject obj = gson.fromJson(resp, JsonObject.class);
                if (obj.has("id")) {
                    userId = obj.get("id").getAsInt();
                    // 持久化存储
                    sp.edit().putInt("user_id", userId).apply();
                    api.setUserId(userId);
                    Log.d(TAG, "resolveUserId: token-based recovery success, userId=" + userId);
                }
            } catch (Exception e) {
                Log.e(TAG, "resolveUserId: token recovery failed", e);
            }
        }
        return userId;
    }

    private void loadHistory() {
        tvEmpty.setText("加载中...");
        tvEmpty.setVisibility(View.VISIBLE);
        rvHistory.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                int userId = resolveUserId();
                Log.d(TAG, "loadHistory: final userId=" + userId);
                if (userId <= 0) {
                    mainHandler.post(() -> {
                        Toast.makeText(HistoryActivity.this, "请先登录", Toast.LENGTH_SHORT).show();
                        tvEmpty.setText("请先登录\n\n请返回重新登录后再试");
                        tvEmpty.setVisibility(View.VISIBLE);
                        rvHistory.setVisibility(View.GONE);
                    });
                    return;
                }

                ApiClient api = ApiClient.getInstance();
                String url = "/chat/conversations?user_id=" + userId;
                Log.d(TAG, "loadHistory: calling " + url);
                String resp = api.getSync(url);
                Log.d(TAG, "loadHistory: response=" + (resp != null ? resp.substring(0, Math.min(200, resp.length())) : "null"));

                JsonArray arr = gson.fromJson(resp, JsonArray.class);

                items.clear();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject obj = arr.get(i).getAsJsonObject();
                    HistoryItem item = new HistoryItem();
                    item.id = obj.has("id") ? String.valueOf(obj.get("id").getAsInt()) : "";
                    item.title = obj.has("title") ? obj.get("title").getAsString() : "新对话";
                    item.msgCount = obj.has("msg_count") ? obj.get("msg_count").getAsInt() : 0;
                    item.time = obj.has("updated_at") ? obj.get("updated_at").getAsString() : "";
                    items.add(item);
                }

                Log.d(TAG, "loadHistory: parsed " + items.size() + " conversations");
                mainHandler.post(() -> {
                    adapter.notifyDataSetChanged();
                    if (items.isEmpty()) {
                        tvEmpty.setText("暂无对话记录\n\n在聊天页面发送消息后\n对话将显示在这里");
                        tvEmpty.setVisibility(View.VISIBLE);
                        rvHistory.setVisibility(View.GONE);
                    } else {
                        tvEmpty.setVisibility(View.GONE);
                        rvHistory.setVisibility(View.VISIBLE);
                    }
                });
            } catch (IOException e) {
                Log.e(TAG, "loadHistory: network error", e);
                mainHandler.post(() -> {
                    Toast.makeText(HistoryActivity.this, "网络连接失败", Toast.LENGTH_SHORT).show();
                    tvEmpty.setText("网络连接失败\n请检查网络后重试\n\n" + e.getMessage());
                    tvEmpty.setVisibility(View.VISIBLE);
                    rvHistory.setVisibility(View.GONE);
                });
            } catch (Exception e) {
                Log.e(TAG, "loadHistory: parse error", e);
                mainHandler.post(() -> {
                    Toast.makeText(HistoryActivity.this, "加载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    tvEmpty.setText("加载失败\n" + e.getMessage());
                    tvEmpty.setVisibility(View.VISIBLE);
                    rvHistory.setVisibility(View.GONE);
                });
            }
        }).start();
    }

    /** 点击历史记录项 → 跳转聊天页查看详情 */
    private void onItemClick(HistoryItem item) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("conversation_id", item.id);
        intent.putExtra("conversation_title", item.title);
        startActivity(intent);
    }

    /** 删除单条对话 */
    private void deleteConversation(HistoryItem item, int position) {
        new Thread(() -> {
            try {
                int userId = resolveUserId();
                String url = "/chat/conversations/" + item.id + "?user_id=" + userId;
                ApiClient.getInstance().getSync(url); // 用 DELETE 不方便，用 GET 兼容。实际用 OkHttp DELETE
                // 用 synchronous delete call
                okhttp3.Request req = new okhttp3.Request.Builder()
                    .url(ApiClient.getInstance().getBaseUrl() + url)
                    .delete()
                    .build();
                okhttp3.Response resp = new okhttp3.OkHttpClient().newCall(req).execute();
                boolean ok = resp.isSuccessful();
                resp.close();
                if (ok) {
                    mainHandler.post(() -> {
                        items.remove(position);
                        adapter.notifyItemRemoved(position);
                        adapter.notifyItemRangeChanged(position, items.size());
                        if (items.isEmpty()) {
                            tvEmpty.setText("暂无对话记录\n\n在聊天页面发送消息后\n对话将显示在这里");
                            tvEmpty.setVisibility(View.VISIBLE);
                            rvHistory.setVisibility(View.GONE);
                        }
                        Toast.makeText(HistoryActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(HistoryActivity.this, "删除失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    /** 清除全部对话 */
    private void clearAll() {
        if (items.isEmpty()) return;
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("清除全部对话")
            .setMessage("确定要删除所有对话记录吗？此操作不可恢复。")
            .setPositiveButton("确定", (dialog, which) -> {
                new Thread(() -> {
                    try {
                        int userId = resolveUserId();
                        String url = "/chat/conversations?user_id=" + userId;
                        okhttp3.Request req = new okhttp3.Request.Builder()
                            .url(ApiClient.getInstance().getBaseUrl() + url)
                            .delete()
                            .build();
                        okhttp3.Response resp = new okhttp3.OkHttpClient().newCall(req).execute();
                        boolean ok = resp.isSuccessful();
                        resp.close();
                        if (ok) {
                            mainHandler.post(() -> {
                                items.clear();
                                adapter.notifyDataSetChanged();
                                tvEmpty.setText("暂无对话记录");
                                tvEmpty.setVisibility(View.VISIBLE);
                                rvHistory.setVisibility(View.GONE);
                                Toast.makeText(HistoryActivity.this, "已清除全部对话", Toast.LENGTH_SHORT).show();
                            });
                        }
                    } catch (Exception e) {
                        mainHandler.post(() -> Toast.makeText(HistoryActivity.this, "清除失败", Toast.LENGTH_SHORT).show());
                    }
                }).start();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    static class HistoryItem {
        String id, title, time;
        int msgCount;
    }

    class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {
        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_history, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            HistoryItem item = items.get(position);
            holder.tvTitle.setText(item.title);
            holder.tvInfo.setText(item.msgCount + " 条消息  " + item.time);
            holder.itemView.setOnClickListener(v -> onItemClick(item));
            holder.btnDelete.setOnClickListener(v -> deleteConversation(item, position));
        }

        @Override
        public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvTitle, tvInfo;
            View btnDelete;
            VH(View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tv_hist_title);
                tvInfo = v.findViewById(R.id.tv_hist_info);
                btnDelete = v.findViewById(R.id.btn_delete);
            }
        }
    }
}
