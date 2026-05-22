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
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

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

    private int userId = 1;
    private String conversationId;
    private boolean isLoading = false;
    private String lastFailedContent = "";
    private boolean transferRequested = false;
    private boolean agentNotified = false;

    private WebSocket ws;
    private View btnTransfer, btnExitAgent;
    private Handler pollHandler = new Handler(Looper.getMainLooper());
    private Runnable pollRunnable;

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

        userId = getIntent().getIntExtra("user_id", 1);
        conversationId = getIntent().getStringExtra("conversation_id");

        initViews();
        initChat();
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

        // 新对话
        findViewById(R.id.btn_new_chat).setOnClickListener(v -> startNewChat());

        // 转人工
        btnTransfer = findViewById(R.id.btn_transfer);
        btnTransfer.setOnClickListener(v -> requestTransfer());

        // 退出人工
        btnExitAgent = findViewById(R.id.btn_exit_agent);
        btnExitAgent.setOnClickListener(v -> exitAgent());

        // 历史按钮
        findViewById(R.id.btn_history).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));

        // 设置
        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        // 管理员入口（长按标题）
        findViewById(R.id.tv_title).setOnLongClickListener(v -> {
            startActivity(new Intent(this, AdminActivity.class));
            return true;
        });
    }

    /** 初始化对话：已有会话则加载历史，否则创建新会话 */
    private void initChat() {
        if (conversationId != null && !conversationId.isEmpty()) {
            // 继续已有对话 — 从服务器加载历史消息
            String title = getIntent().getStringExtra("conversation_title");
            loadConversationHistory(conversationId, title);
        } else {
            // 新建对话 — 不发消息不创建会话，由 chat/send 自动创建
            Message welcome = new Message(0, "ai",
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
        }
    }

    /** 从服务器加载历史消息 */
    private void loadConversationHistory(String convId, String title) {
        new Thread(() -> {
            try {
                String resp = ApiClient.getInstance().getSync("/chat/history/" + convId);
                JsonArray arr = gson.fromJson(resp, JsonArray.class);
                mainHandler.post(() -> {
                    // 先显示提示
                    adapter.addMessage(new Message(0, "ai",
                            "已回到对话：\"" + (title != null ? title : "历史对话") + "\"",
                            getCurrentTime()));
                    // 显示转人工按钮（已有会话）
                    btnTransfer.setVisibility(View.VISIBLE);
                    connectWS(convId);
                    // 加载历史消息
                    for (int i = 0; i < arr.size(); i++) {
                        JsonObject obj = arr.get(i).getAsJsonObject();
                        String role = obj.has("role") ? obj.get("role").getAsString() : "user";
                        String content = obj.has("content") ? obj.get("content").getAsString() : "";
                        String time = obj.has("created_at") ? obj.get("created_at").getAsString() : "";
                        // 截取时间 HH:mm
                        if (time.length() >= 16) {
                            time = time.substring(11, 16);
                        }
                        adapter.addMessage(new Message(
                                obj.has("id") ? obj.get("id").getAsInt() : generateId(),
                                role, content, time));
                    }
                    scrollToBottom();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    adapter.addMessage(new Message(0, "ai",
                            "无法加载历史消息，请检查网络后重试。", getCurrentTime()));
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
        Message userMsg = new Message(generateId(), "user", content, getCurrentTime());
        adapter.addMessage(userMsg);
        scrollToBottom();

        // 转人工模式下优先通过WebSocket发送
        if (transferRequested && ws != null) {
            try {
                boolean sent = ws.send(createWsMessage(content));
                if (sent) {
                    isLoading = false;
                    adapter.setShowLoading(false);
                    return;
                }
            } catch (Exception e) {
                // WS发送失败，重连后走REST
                connectWS(conversationId);
            }
        }

        // 构造REST请求
        JsonObject body = new JsonObject();
        body.addProperty("message", content);
        body.addProperty("user_id", userId);
        if (conversationId != null) {
            body.addProperty("conversation_id", Integer.parseInt(conversationId));
        }

        ApiClient.getInstance().post("/chat/send", body, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> {
                    isLoading = false;
                    adapter.setShowLoading(false);
                    Message errMsg = new Message(generateId(), "ai",
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
                        }
                        // 首次发消息时记录服务器返回的 conversation_id
                        if (conversationId == null && res.has("conversation_id")) {
                            conversationId = String.valueOf(res.get("conversation_id").getAsInt());
                            final String cid = conversationId;
                            runOnUiThread(() -> {
                                // 如果已请求转人工，此时立即连接WebSocket
                                if (transferRequested && cid != null) {
                                    connectWS(cid);
                                }
                            });
                        }
                    }
                } catch (Exception ignored) {}

                final String finalReply = replyText;
                mainHandler.post(() -> {
                    isLoading = false;
                    adapter.setShowLoading(false);
                    if (!finalReply.isEmpty()) {
                        Message aiMsg = new Message(generateId(), "ai", finalReply, getCurrentTime());
                        adapter.addMessage(aiMsg);
                    }
                    scrollToBottom();
                    // 如果回复包含转人工提示，自动启动轮询等待客服
                    if (!transferRequested && finalReply.contains("转人工")) {
                        transferRequested = true;
                        agentNotified = false;
                        btnTransfer.setVisibility(View.GONE);
                        btnExitAgent.setVisibility(View.GONE);
                        startPolling();
                        Toast.makeText(ChatActivity.this,
                                "已提交转人工请求，请耐心等待客服接入", Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    /** 开始新对话 */
    private void startNewChat() {
        conversationId = null;
        transferRequested = false;
        agentNotified = false;
        btnTransfer.setVisibility(View.VISIBLE);
        btnExitAgent.setVisibility(View.GONE);
        disconnectWS();
        stopPolling();
        adapter.clearMessages();
        initChat();
        scrollToBottom();
    }

    /** 请求转人工 — 统一走 sendMessage 路径，后端自动识别 */
    private void requestTransfer() {
        if (transferRequested) {
            Toast.makeText(this, "已提交转人工请求，请等待客服接入", Toast.LENGTH_SHORT).show();
            return;
        }
        // 直接输入"转人工"发送，和后端关键词检测共用同一逻辑
        etInput.setText("转人工");
        sendMessage();
    }

    /** 记录已显示的agent消息ID，避免重复 */
    private int lastAgentMsgId = 0;

    /** 轮询检查客服是否接入 + 拉取新消息 */
    private void startPolling() {
        stopPolling();
        lastAgentMsgId = 0;
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (conversationId == null) {
                    pollHandler.postDelayed(pollRunnable, 1500);
                    return;
                }
                new Thread(() -> {
                    try {
                        String resp = ApiClient.getInstance().getSync("/chat/history/" + conversationId);
                        JsonArray arr = gson.fromJson(resp, JsonArray.class);
                        for (int i = 0; i < arr.size(); i++) {
                            JsonObject msg = arr.get(i).getAsJsonObject();
                            String role = msg.has("role") ? msg.get("role").getAsString() : "";
                            int msgId = msg.has("id") ? msg.get("id").getAsInt() : 0;
                            String content = msg.has("content") ? msg.get("content").getAsString() : "";
                            String time = msg.has("created_at") ? msg.get("created_at").getAsString() : "";
                            if (time.length() >= 16) time = time.substring(11, 16);

                            // 客服接入首次通知
                            if ("agent".equals(role) && !agentNotified) {
                                agentNotified = true;
                                mainHandler.post(() -> {
                                    btnTransfer.setVisibility(View.GONE);
                                    btnExitAgent.setVisibility(View.VISIBLE);
                                    Toast.makeText(ChatActivity.this,
                                            "人工客服已接入，请直接输入您的问题", Toast.LENGTH_LONG).show();
                                    adapter.addMessage(new Message(generateId(), "system",
                                            "人工客服已接入，请直接输入您的问题", getCurrentTime()));
                                });
                            }

                            // 客服结束对话通知
                            if ("system".equals(role) && msgId > lastAgentMsgId && content.contains("已结束对话")) {
                                lastAgentMsgId = msgId;
                                final int fMsgId = msgId;
                                final String fContent = content;
                                final String fTime = time;
                                mainHandler.post(() -> {
                                    adapter.addMessage(new Message(fMsgId, "system", fContent, fTime));
                                    scrollToBottom();
                                    Toast.makeText(ChatActivity.this,
                                            "人工客服已结束对话", Toast.LENGTH_LONG).show();
                                    stopPolling();
                                });
                                break;
                            }

                            // 显示新的agent消息
                            if ("agent".equals(role) && msgId > lastAgentMsgId && !TextUtils.isEmpty(content)) {
                                lastAgentMsgId = msgId;
                                final int fMsgId = msgId;
                                final String fContent = content;
                                final String fTime = time;
                                mainHandler.post(() -> {
                                    adapter.addMessage(new Message(fMsgId, "agent", fContent, fTime));
                                    scrollToBottom();
                                    Toast.makeText(ChatActivity.this,
                                            "客服: " + fContent.substring(0, Math.min(20, fContent.length())),
                                            Toast.LENGTH_SHORT).show();
                                });
                            }
                        }
                    } catch (Exception ignored) {}
                    pollHandler.postDelayed(pollRunnable, 1500);
                }).start();
            }
        };
        pollHandler.postDelayed(pollRunnable, 1500);
    }

    private void stopPolling() {
        if (pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
            pollRunnable = null;
        }
    }

    /** 退出人工客服 */
    private void exitAgent() {
        transferRequested = false;
        agentNotified = false;
        stopPolling();
        disconnectWS();
        btnTransfer.setVisibility(View.VISIBLE);
        btnExitAgent.setVisibility(View.GONE);
        adapter.addMessage(new Message(generateId(), "system",
                "已退出人工客服，AI助手将继续为您服务", getCurrentTime()));
        scrollToBottom();
        Toast.makeText(this, "已退出人工客服", Toast.LENGTH_SHORT).show();

        // 通知后端关闭转人工
        if (conversationId != null) {
            ApiClient.getInstance().post(
                "/agent/transfer-requests/by-conversation/" + conversationId + "/close",
                null, new okhttp3.Callback() {
                    @Override public void onFailure(okhttp3.Call c, java.io.IOException e) {}
                    @Override public void onResponse(okhttp3.Call c, okhttp3.Response r) { r.close(); }
                });
        }
    }

    /** 连接WebSocket (作为user角色) */
    private void connectWS(String convId) {
        disconnectWS();
        OkHttpClient wsClient = new OkHttpClient.Builder()
                .pingInterval(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build();
        Request wsReq = new Request.Builder()
                .url("ws://8.137.205.18/ws/chat/" + convId + "/user")
                .header("User-Agent", "AirPodsAssistant-Android")
                .build();
        ws = wsClient.newWebSocket(wsReq, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                mainHandler.post(() ->
                        Toast.makeText(ChatActivity.this,
                                "已连接客服通道", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    JsonObject msg = gson.fromJson(text, JsonObject.class);
                    String role = msg.has("role") ? msg.get("role").getAsString() : "agent";
                    String content = msg.has("content") ? msg.get("content").getAsString() : "";
                    String type = msg.has("type") ? msg.get("type").getAsString() : "";
                    if (!TextUtils.isEmpty(content)) {
                        mainHandler.post(() -> {
                            // 客服接入通知
                            if ("agent_connected".equals(type)) {
                                Toast.makeText(ChatActivity.this,
                                        "人工客服已接入，请直接输入您的问题", Toast.LENGTH_LONG).show();
                                adapter.addMessage(new Message(generateId(),
                                        "system", content, getCurrentTime()));
                            } else {
                                int id = generateId();
                                adapter.addMessage(new Message(id,
                                        "agent".equals(role) ? "agent" : role,
                                        content, getCurrentTime()));
                                if ("agent".equals(role)) {
                                    // 同步lastAgentMsgId避免轮询重复显示
                                    lastAgentMsgId = Math.max(lastAgentMsgId, id);
                                    Toast.makeText(ChatActivity.this,
                                            "客服: " + content.substring(0, Math.min(30, content.length())),
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                            scrollToBottom();
                        });
                    }
                } catch (Exception ignored) {}
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                // 断连后如果还在转人工模式，3秒后重连
                if (transferRequested && conversationId != null) {
                    mainHandler.postDelayed(() -> {
                        if (transferRequested && conversationId != null) {
                            connectWS(conversationId);
                        }
                    }, 3000);
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                String err = t != null ? t.getMessage() : "unknown";
                if (response != null) err += " code:" + response.code();
                final String finalErr = err;
                mainHandler.post(() -> {
                    // 断连后重连
                    if (transferRequested && conversationId != null) {
                        mainHandler.postDelayed(() -> connectWS(conversationId), 3000);
                    }
                    Toast.makeText(ChatActivity.this,
                            "客服连接断开，正在重连...", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void disconnectWS() {
        if (ws != null) {
            try { ws.close(1000, "done"); } catch (Exception ignored) {}
            ws = null;
        }
    }

    private String createWsMessage(String content) {
        JsonObject msg = new JsonObject();
        msg.addProperty("content", content);
        return gson.toJson(msg);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnectWS();
        stopPolling();
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
