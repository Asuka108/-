package com.airpods.assistant;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import java.util.Collections;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * 对话历史页
 */
public class HistoryActivity extends AppCompatActivity {

    private RecyclerView rvHistory;
    private HistoryAdapter adapter;
    private final List<HistoryItem> items = Collections.synchronizedList(new ArrayList<>());
    private Gson gson = new Gson();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_history);

            rvHistory = findViewById(R.id.rv_history);
            rvHistory.setLayoutManager(new LinearLayoutManager(this));
            adapter = new HistoryAdapter();
            rvHistory.setAdapter(adapter);

            findViewById(R.id.btn_back).setOnClickListener(v -> finish());

            loadHistory();
        } catch (Exception e) {
            Toast.makeText(this, "页面加载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void loadHistory() {
        SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
        String userId = String.valueOf(prefs.getInt("user_id", 1));

        ApiClient.getInstance().get("/chat/conversations",
                Collections.singletonMap("user_id", userId),
                new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        mainHandler.post(() ->
                                Toast.makeText(HistoryActivity.this,
                                        "网络连接失败，请检查网络", Toast.LENGTH_SHORT).show());
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (!response.isSuccessful() || response.body() == null) {
                            mainHandler.post(() ->
                                    Toast.makeText(HistoryActivity.this,
                                            "加载失败，请稍后重试", Toast.LENGTH_SHORT).show());
                            return;
                        }
                        String resp = response.body().string();
                        try {
                            JsonArray arr = gson.fromJson(resp, JsonArray.class);
                            items.clear();
                            for (int i = 0; i < arr.size(); i++) {
                                JsonObject obj = arr.get(i).getAsJsonObject();
                                HistoryItem item = new HistoryItem();
                                item.id = obj.has("id") ? obj.get("id").getAsString() : "";
                                item.title = obj.has("title") ? obj.get("title").getAsString() : "未命名对话";
                                item.time = obj.has("updated_at") ? obj.get("updated_at").getAsString()
                                        : obj.has("created_at") ? obj.get("created_at").getAsString() : "";
                                items.add(item);
                            }
                            mainHandler.post(() -> adapter.notifyDataSetChanged());
                        } catch (Exception e) {
                            mainHandler.post(() ->
                                    Toast.makeText(HistoryActivity.this,
                                            "数据解析失败", Toast.LENGTH_SHORT).show());
                        }
                    }
                });
    }

    static class HistoryItem {
        String id, title, time;
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
            holder.tvInfo.setText(item.time);
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(HistoryActivity.this, ChatActivity.class);
                intent.putExtra("conversation_id", item.id);
                intent.putExtra("conversation_title", item.title);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvTitle, tvInfo;
            VH(View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tv_hist_title);
                tvInfo = v.findViewById(R.id.tv_hist_info);
            }
        }
    }
}
