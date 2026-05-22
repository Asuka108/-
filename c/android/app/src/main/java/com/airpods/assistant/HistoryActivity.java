package com.airpods.assistant;

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

import java.util.ArrayList;
import java.util.List;

/**
 * 对话历史页
 */
public class HistoryActivity extends AppCompatActivity {

    private RecyclerView rvHistory;
    private HistoryAdapter adapter;
    private List<HistoryItem> items = new ArrayList<>();
    private Gson gson = new Gson();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        rvHistory = findViewById(R.id.rv_history);
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter();
        rvHistory.setAdapter(adapter);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        loadHistory();
    }

    private void loadHistory() {
        new Thread(() -> {
            try {
                ApiClient api = ApiClient.getInstance();
                int userId = api.getUserId();
                if (userId <= 0) {
                    mainHandler.post(() ->
                            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show());
                    return;
                }
                String resp = api.getSync("/chat/conversations?user_id=" + userId);
                JsonArray arr = gson.fromJson(resp, JsonArray.class);
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject obj = arr.get(i).getAsJsonObject();
                    HistoryItem item = new HistoryItem();
                    item.id = obj.has("id") ? String.valueOf(obj.get("id").getAsInt()) : "";
                    item.title = obj.has("title") ? obj.get("title").getAsString() : "新对话";
                    item.msgCount = obj.has("msg_count") ? obj.get("msg_count").getAsInt() : 0;
                    item.time = obj.has("updated_at") ? obj.get("updated_at").getAsString() : "";
                    items.add(item);
                }
                mainHandler.post(() -> adapter.notifyDataSetChanged());
            } catch (Exception e) {
                mainHandler.post(() ->
                        Toast.makeText(HistoryActivity.this, "加载历史记录失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
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
        }

        @Override
        public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            android.widget.TextView tvTitle, tvInfo;
            VH(View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tv_hist_title);
                tvInfo = v.findViewById(R.id.tv_hist_info);
            }
        }
    }
}
