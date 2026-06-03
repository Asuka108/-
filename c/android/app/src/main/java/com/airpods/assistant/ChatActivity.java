package com.airpods.assistant;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airpods.assistant.adapter.ChatAdapter;
import com.airpods.assistant.api.ApiClient;
import com.airpods.assistant.model.Message;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * 聊天主界面 Activity
 * Pineapple 风格：蓝色主题，气泡式聊天
 */
public class ChatActivity extends AppCompatActivity {

    private RecyclerView rvMessages;
    private EditText etInput;
    private View btnSend;
    private ChatAdapter adapter;
    private LinearLayoutManager layoutManager;

    private String conversationId;
    private boolean isLoading = false;
    private String lastFailedContent = "";

    private Gson gson = new Gson();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // 快捷问题
    private String[] quickQuestions = {
            "AirPods怎么连接？",
            "左耳没声音怎么办？",
            "保修期多久？",
            "如何辨别真伪？",
            "电池不耐用怎么办？"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // 检查是否从历史记录进入（带conversation_id）
        String existingConvId = getIntent().getStringExtra("conversation_id");
        initViews();

        if (existingConvId != null && !existingConvId.isEmpty()) {
            conversationId = existingConvId;
            loadConversationHistory(existingConvId);
        } else {
            initChat();
        }
    }

    private void initViews() {
        rvMessages = findViewById(R.id.rv_messages);
        etInput = findViewById(R.id.et_input);
        btnSend = findViewById(R.id.btn_send);

        layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
        adapter = new ChatAdapter();
        rvMessages.setAdapter(adapter);

        // 发送按钮
        btnSend.setOnClickListener(v -> sendMessage());

        // 键盘发送键
        etInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });

        // 快捷问题点击
        int[] tagIds = {R.id.tag_q1, R.id.tag_q2, R.id.tag_q3, R.id.tag_q4, R.id.tag_q5};
        for (int i = 0; i < 5; i++) {
            TextView tag = findViewById(tagIds[i]);
            final int idx = i;
            tag.setOnClickListener(v -> {
                etInput.setText(quickQuestions[idx]);
                sendMessage();
            });
        }

        // 历史按钮
        findViewById(R.id.btn_history).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));

        // 管理员入口（长按标题）
        findViewById(R.id.tv_title).setOnLongClickListener(v -> {
            startActivity(new Intent(this, AdminActivity.class));
            return true;
        });
    }

    /** 初始化对话：创建新会话 + 欢迎消息 */
    private void initChat() {
        // 欢迎消息
        Message welcome = new Message(0, "ai", "ai",
                "您好！我是菠萝耳机售后AI助手\n\n" +
                        "我可以帮您：\n" +
                        "• 排查连接和配对问题\n" +
                        "• 解决音质和降噪故障\n" +
                        "• 查询保修政策和维修费用\n" +
                        "• 辨别 AirPods 真伪\n" +
                        "• 对比各型号差异\n\n" +
                        "请直接输入您的问题，或点击下方快捷入口开始吧！",
                getCurrentTime());
        adapter.addMessage(welcome);

        // 异步创建对话
        new Thread(() -> {
            try {
                int uid = ApiClient.getInstance().getUserId();
                if (uid <= 0) {
                    SharedPreferences sp = getSharedPreferences("auth", MODE_PRIVATE);
                    uid = sp.getInt("user_id", 1);
                }
                JsonObject req = new JsonObject();
                req.addProperty("title", "新对话");
                req.addProperty("user_id", uid);
                String resp = ApiClient.getInstance().postSync("/chat/conversations", req);
                JsonObject json = gson.fromJson(resp, JsonObject.class);
                if (json.has("conversation_id")) {
                    conversationId = json.get("conversation_id").getAsString();
                }
            } catch (Exception ignored) {
                // 离线模式也可使用
            }
        }).start();
    }

    /** 加载已有对话的历史消息 */
    private void loadConversationHistory(String convId) {
        new Thread(() -> {
            try {
                String resp = ApiClient.getInstance().getSync("/chat/history/" + convId);
                JsonArray arr = gson.fromJson(resp, JsonArray.class);
                List<Message> historyMsgs = new ArrayList<>();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject obj = arr.get(i).getAsJsonObject();
                    String role = obj.has("role") ? obj.get("role").getAsString() : "user";
                    String content = obj.has("content") ? obj.get("content").getAsString() : "";
                    String time = obj.has("created_at") ? obj.get("created_at").getAsString() : getCurrentTime();
                    String source = "user".equals(role) ? "user" : "ai";
                    historyMsgs.add(new Message(generateId(), role, source, content, time));
                }
                mainHandler.post(() -> {
                    adapter.clearMessages();
                    for (Message m : historyMsgs) {
                        adapter.addMessage(m);
                    }
                    scrollToBottom();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    Message errMsg = new Message(0, "ai", "ai",
                            "加载历史消息失败，请检查网络后重试。", getCurrentTime());
                    adapter.addMessage(errMsg);
                });
            }
        }).start();
    }

    /** 发送消息 */
    private void sendMessage() {
        String content = etInput.getText().toString().trim();
        if (TextUtils.isEmpty(content) || isLoading) return;

        lastFailedContent = content;
        etInput.setText("");
        isLoading = true;
        adapter.setShowLoading(true);
        scrollToBottom();

        // 添加用户消息
        Message userMsg = new Message(generateId(), "user", "user", content, getCurrentTime());
        adapter.addMessage(userMsg);
        scrollToBottom();

        // 构造请求
        JsonObject body = new JsonObject();
        body.addProperty("message", content);
        int uid = ApiClient.getInstance().getUserId();
        if (uid <= 0) {
            SharedPreferences sp = getSharedPreferences("auth", MODE_PRIVATE);
            uid = sp.getInt("user_id", 1);
        }
        body.addProperty("user_id", uid);
        if (conversationId != null) {
            body.addProperty("conversation_id", Integer.parseInt(conversationId));
        }

        ApiClient.getInstance().post("/chat/send", body, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> {
                    isLoading = false;
                    adapter.setShowLoading(false);
                    Message errMsg = new Message(generateId(), "ai", "ai",
                            "抱歉，网络连接失败，请检查网络后重试。", getCurrentTime());
                    adapter.addMessage(errMsg);
                    scrollToBottom();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String replyText = "抱歉，我暂时无法回复，请稍后重试。";
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        JsonObject res = gson.fromJson(json, JsonObject.class);
                        if (res.has("reply")) {
                            replyText = res.get("reply").getAsString();
                        } else if (res.has("data") && res.getAsJsonObject("data").has("reply")) {
                            replyText = res.getAsJsonObject("data").get("reply").getAsString();
                        }
                        // 保存conversation_id，避免每次消息创建新会话
                        if (conversationId == null && res.has("conversation_id")) {
                            conversationId = String.valueOf(res.get("conversation_id").getAsInt());
                        }
                    }
                } catch (Exception ignored) {}

                final String finalReply = replyText;
                mainHandler.post(() -> {
                    isLoading = false;
                    adapter.setShowLoading(false);
                    Message aiMsg = new Message(generateId(), "ai", "ai", finalReply, getCurrentTime());
                    adapter.addMessage(aiMsg);
                    scrollToBottom();
                });
            }
        });
    }

    private void scrollToBottom() {
        rvMessages.postDelayed(() ->
                rvMessages.smoothScrollToPosition(adapter.getItemCount() - 1), 100);
    }

    private int generateId() {
        return (int) (System.currentTimeMillis() % Integer.MAX_VALUE);
    }

    private String getCurrentTime() {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
    }
}
