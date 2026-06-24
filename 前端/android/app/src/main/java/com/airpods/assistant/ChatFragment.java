package com.airpods.assistant;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
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
 * 聊天界面 Fragment — AI客服 + 人工客服
 */
public class ChatFragment extends Fragment {

    private RecyclerView rvMessages;
    private EditText etInput;
    private View btnSend;
    private ChatAdapter adapter;
    private LinearLayoutManager layoutManager;

    private int userId = 1;
    private String conversationId;
    private boolean isLoading = false;
    private boolean transferRequested = false;
    private boolean agentNotified = false;

    private WebSocket ws;
    private View btnTransfer, btnExitAgent;
    private Handler pollHandler = new Handler(Looper.getMainLooper());
    private Runnable pollRunnable;
    private int lastAgentMsgId = 0;

    private Gson gson = new Gson();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private String[] quickQuestions = {
            "AirPods怎么连接？",
            "左耳没声音怎么办？",
            "保修期多久？",
            "如何辨别真伪？",
            "电池不耐用怎么办？"
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SharedPreferences sp = requireActivity().getSharedPreferences("auth", 0);
        userId = sp.getInt("user_id", 1);

        initViews(view);
        initChat();
    }

    private void initViews(View view) {
        rvMessages = view.findViewById(R.id.rv_messages);
        etInput = view.findViewById(R.id.et_input);
        btnSend = view.findViewById(R.id.btn_send);

        layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
        adapter = new ChatAdapter();
        rvMessages.setAdapter(adapter);

        btnSend.setOnClickListener(v -> sendMessage());
        etInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); return true; }
            return false;
        });

        int[] tagIds = {R.id.tag_q1, R.id.tag_q2, R.id.tag_q3, R.id.tag_q4, R.id.tag_q5};
        for (int i = 0; i < 5; i++) {
            TextView tag = view.findViewById(tagIds[i]);
            final int idx = i;
            tag.setOnClickListener(v -> { etInput.setText(quickQuestions[idx]); sendMessage(); });
        }

        view.findViewById(R.id.btn_new_chat).setOnClickListener(v -> startNewChat());

        btnTransfer = view.findViewById(R.id.btn_transfer);
        btnTransfer.setOnClickListener(v -> requestTransfer());

        btnExitAgent = view.findViewById(R.id.btn_exit_agent);
        btnExitAgent.setOnClickListener(v -> exitAgent());

        view.findViewById(R.id.btn_history).setOnClickListener(v ->
                startActivity(new Intent(getActivity(), HistoryActivity.class)));

        view.findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(getActivity(), SettingsActivity.class)));

        view.findViewById(R.id.tv_title).setOnLongClickListener(v -> {
            startActivity(new Intent(getActivity(), AdminActivity.class));
            return true;
        });
    }

    private void initChat() {
        Message welcome = new Message(0, "ai", "ai",
                "您好！我是菠萝耳机售后AI助手\n\n" +
                        "我可以帮您：\n" +
                        "• 排查连接和配对问题\n" +
                        "• 解决音质和降噪故障\n" +
                        "• 查询保修政策和维修费用\n" +
                        "• 辨别 AirPods 真伪\n" +
                        "• 对比各型号差异\n\n" +
                        "请直接输入您的问题，或点击下方快捷入口开始吧！\n\n" +
                        "如需人工客服，请点击右上角「转人工」",
                getCurrentTime());
        adapter.addMessage(welcome);
    }

    private void startNewChat() {
        conversationId = null;
        transferRequested = false;
        agentNotified = false;
        lastAgentMsgId = 0;
        btnTransfer.setVisibility(View.VISIBLE);
        btnExitAgent.setVisibility(View.GONE);
        disconnectWS();
        stopPolling();
        adapter.clearMessages();
        initChat();
    }

    private void requestTransfer() {
        if (transferRequested) {
            Toast.makeText(getContext(), "已提交转人工请求，请等待客服接入", Toast.LENGTH_SHORT).show();
            return;
        }
        etInput.setText("转人工");
        sendMessage();
    }

    private void exitAgent() {
        transferRequested = false;
        agentNotified = false;
        stopPolling();
        disconnectWS();
        btnTransfer.setVisibility(View.VISIBLE);
        btnExitAgent.setVisibility(View.GONE);
        adapter.addMessage(new Message(generateId(), "system", "system",
                "已退出人工客服，AI助手将继续为您服务", getCurrentTime()));
        Toast.makeText(getContext(), "已退出人工客服", Toast.LENGTH_SHORT).show();

        // 清空会话，让AI重新开始新对话
        conversationId = null;
        lastAgentMsgId = 0;
    }

    private void sendMessage() {
        String content = etInput.getText().toString().trim();
        if (TextUtils.isEmpty(content) || isLoading) return;

        etInput.setText("");
        isLoading = true;
        adapter.setShowLoading(true);
        scrollToBottom();

        Message userMsg = new Message(generateId(), "user", "user", content, getCurrentTime());
        adapter.addMessage(userMsg);
        scrollToBottom();

        // 转人工模式：只通过WebSocket发给客服，不走AI
        if (transferRequested && agentNotified) {
            if (ws != null) {
                try {
                    JsonObject msg = new JsonObject();
                    msg.addProperty("content", content);
                    ws.send(gson.toJson(msg));
                } catch (Exception e) {
                    connectWS(conversationId);
                }
            }
            isLoading = false;
            adapter.setShowLoading(false);
            return;
        }

        // AI模式：通过REST API发送
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
                    adapter.addMessage(new Message(generateId(), "ai", "ai",
                            "抱歉，网络连接失败，请检查网络后重试。", getCurrentTime()));
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
                        if (res.has("reply")) replyText = res.get("reply").getAsString();
                        if (conversationId == null && res.has("conversation_id")) {
                            conversationId = String.valueOf(res.get("conversation_id").getAsInt());
                        }
                    }
                } catch (Exception ignored) {}

                final String finalReply = replyText;
                mainHandler.post(() -> {
                    isLoading = false;
                    adapter.setShowLoading(false);
                    adapter.addMessage(new Message(generateId(), "ai", "ai", finalReply, getCurrentTime()));
                    scrollToBottom();
                    // 检测用户消息中的转人工关键词
                    if (!transferRequested && content.contains("转人工")) {
                        transferRequested = true;
                        agentNotified = false;
                        btnTransfer.setVisibility(View.GONE);
                        btnExitAgent.setVisibility(View.GONE);
                        startPolling();
                        Toast.makeText(getContext(),
                                "已提交转人工请求，请耐心等待客服接入", Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    /** 轮询检查客服接入 + 拉取新消息 */
    private void startPolling() {
        stopPolling();
        lastAgentMsgId = 0;
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!transferRequested) return;
                if (conversationId == null) {
                    pollHandler.postDelayed(this, 2000);
                    return;
                }
                new Thread(() -> {
                    try {
                        String json = ApiClient.getInstance().getSync("/chat/history/" + conversationId);
                        JsonArray arr = gson.fromJson(json, JsonArray.class);
                        for (int i = 0; i < arr.size(); i++) {
                            JsonObject msg = arr.get(i).getAsJsonObject();
                            String role = msg.has("role") ? msg.get("role").getAsString() : "";
                            int msgId = msg.has("id") ? msg.get("id").getAsInt() : 0;
                            String content = msg.has("content") ? msg.get("content").getAsString() : "";
                            String timeRaw = msg.has("created_at") ? msg.get("created_at").getAsString() : "";
                            final String time = timeRaw.length() >= 16 ? timeRaw.substring(11, 16) : timeRaw;

                        // 客服首次接入
                        if ("agent".equals(role) && !agentNotified) {
                            agentNotified = true;
                            if (msgId > lastAgentMsgId) lastAgentMsgId = msgId;
                            mainHandler.post(() -> {
                                btnTransfer.setVisibility(View.GONE);
                                btnExitAgent.setVisibility(View.VISIBLE);
                                Toast.makeText(getContext(),
                                        "人工客服已接入，请直接输入您的问题", Toast.LENGTH_LONG).show();
                                adapter.addMessage(new Message(generateId(), "system", "system",
                                        "人工客服已接入，请直接输入您的问题", getCurrentTime()));
                                scrollToBottom();
                            });
                            connectWS(conversationId);
                        }

                        // 客服结束对话
                        if ("system".equals(role) && msgId > lastAgentMsgId && content.contains("已结束对话")) {
                            lastAgentMsgId = msgId;
                            mainHandler.post(() -> {
                                adapter.addMessage(new Message(msgId, "system", "system", content, time));
                                scrollToBottom();
                                Toast.makeText(getContext(), "人工客服已结束对话", Toast.LENGTH_LONG).show();
                                stopPolling();
                            });
                            return;
                        }

                        // 追踪最新消息ID（WebSocket会推送客服消息，轮询不重复显示）
                        if ("agent".equals(role) && msgId > lastAgentMsgId) {
                            lastAgentMsgId = msgId;
                            // 仅在WebSocket未连接时用轮询显示客服消息
                            if (ws == null) {
                                final String fContent = content;
                                mainHandler.post(() -> {
                                    adapter.addMessage(new Message(generateId(), "agent", "agent", fContent, time));
                                    scrollToBottom();
                                });
                            }
                        }
                        }
                    } catch (Exception e) {
                        // 请求失败，稍后重试
                    }
                    if (transferRequested) {
                        pollHandler.postDelayed(this, 2000);
                    }
                }).start();
            }
        };
        pollHandler.post(pollRunnable);
    }

    private void stopPolling() {
        if (pollRunnable != null) { pollHandler.removeCallbacks(pollRunnable); pollRunnable = null; }
    }

    /** 连接WebSocket接收客服实时消息 */
    private void connectWS(String convId) {
        if (convId == null) return;
        disconnectWS();
        OkHttpClient wsClient = new OkHttpClient.Builder()
                .pingInterval(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build();
        // 从API地址推导WebSocket地址
        String apiToken = ApiClient.getInstance().getToken();
        String wsUrl = ApiClient.getInstance().getBaseUrl()
                .replace("http://", "ws://")
                .replace("https://", "wss://")
                .replace("/api/v1", "") + "/ws/chat/" + convId + "/user"
                + (apiToken != null && !apiToken.isEmpty() ? "?token=" + apiToken : "");
        Request wsReq = new Request.Builder()
                .url(wsUrl)
                .build();
        ws = wsClient.newWebSocket(wsReq, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                mainHandler.post(() -> Toast.makeText(getContext(),
                        "已连接客服通道", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    JsonObject msg = gson.fromJson(text, JsonObject.class);
                    String role = msg.has("role") ? msg.get("role").getAsString() : "agent";
                    String content = msg.has("content") ? msg.get("content").getAsString() : "";
                    // 更新lastAgentMsgId，防止轮询重复（后端现在附带id）
                    if (msg.has("id")) {
                        int msgId = msg.get("id").getAsInt();
                        if (msgId > lastAgentMsgId) lastAgentMsgId = msgId;
                    }
                    if (!TextUtils.isEmpty(content) && "agent".equals(role)) {
                        mainHandler.post(() -> {
                            adapter.addMessage(new Message(generateId(), "agent", "agent",
                                    content, getCurrentTime()));
                            scrollToBottom();
                        });
                    }
                } catch (Exception ignored) {}
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                if (transferRequested && conversationId != null) {
                    mainHandler.postDelayed(() -> {
                        if (transferRequested && conversationId != null) connectWS(conversationId);
                    }, 3000);
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                if (transferRequested && conversationId != null) {
                    mainHandler.postDelayed(() -> connectWS(conversationId), 3000);
                }
            }
        });
    }

    private void disconnectWS() {
        if (ws != null) { try { ws.close(1000, "done"); } catch (Exception ignored) {} ws = null; }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
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
