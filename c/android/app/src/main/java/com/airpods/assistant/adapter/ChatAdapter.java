package com.airpods.assistant.adapter;

import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.airpods.assistant.R;
import com.airpods.assistant.model.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天消息适配器 — 支持 AI/用户/客服/系统 消息
 */
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_AI = 0;
    private static final int TYPE_USER = 1;
    private static final int TYPE_LOADING = 2;
    private static final int TYPE_AGENT = 3;
    private static final int TYPE_SYSTEM = 4;

    private List<Message> messages = new ArrayList<>();
    private boolean showLoading = false;

    public void setMessages(List<Message> messages) {
        this.messages = messages != null ? messages : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void clearMessages() {
        this.messages.clear();
        this.showLoading = false;
        notifyDataSetChanged();
    }

    public void addMessage(Message message) {
        this.messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void setShowLoading(boolean show) {
        if (this.showLoading != show) {
            this.showLoading = show;
            if (show) notifyItemInserted(messages.size());
            else notifyItemRemoved(messages.size());
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (position < messages.size()) {
            Message msg = messages.get(position);
            if (msg.isUser()) return TYPE_USER;
            if ("agent".equals(msg.getRole())) return TYPE_AGENT;
            if ("system".equals(msg.getRole())) return TYPE_SYSTEM;
            return TYPE_AI;
        }
        return TYPE_LOADING;
    }

    @Override
    public int getItemCount() {
        return messages.size() + (showLoading ? 1 : 0);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_USER) {
            return new UserHolder(inflater.inflate(R.layout.item_message_user, parent, false));
        } else if (viewType == TYPE_AGENT) {
            // 客服用AI布局，颜色不同
            return new AiHolder(inflater.inflate(R.layout.item_message_ai, parent, false));
        } else if (viewType == TYPE_SYSTEM) {
            TextView tv = new TextView(parent.getContext());
            tv.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(24, 16, 24, 16);
            tv.setTextSize(13);
            tv.setTextColor(Color.parseColor("#8E8E93"));
            return new SystemHolder(tv);
        } else if (viewType == TYPE_LOADING) {
            return new LoadingHolder(inflater.inflate(R.layout.item_loading, parent, false));
        } else {
            return new AiHolder(inflater.inflate(R.layout.item_message_ai, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof UserHolder) {
            Message msg = messages.get(position);
            ((UserHolder) holder).content.setText(msg.getContent());
            ((UserHolder) holder).time.setText(msg.getTime());
        } else if (holder instanceof AiHolder) {
            Message msg = messages.get(position);
            ((AiHolder) holder).content.setText(msg.getContent());
            ((AiHolder) holder).time.setText(msg.getTime());
            // 客服消息显示标签
            if ("agent".equals(msg.getRole()) && ((AiHolder) holder).label != null) {
                ((AiHolder) holder).label.setVisibility(View.VISIBLE);
                ((AiHolder) holder).label.setText("🧑‍💼 人工客服");
            } else if (((AiHolder) holder).label != null) {
                ((AiHolder) holder).label.setVisibility(View.GONE);
            }
        } else if (holder instanceof SystemHolder) {
            Message msg = messages.get(position);
            ((SystemHolder) holder).setText(msg.getContent());
        }
    }

    static class AiHolder extends RecyclerView.ViewHolder {
        TextView content, time, label;
        AiHolder(View v) {
            super(v);
            label = v.findViewById(R.id.tv_ai_label);
            content = v.findViewById(R.id.tv_ai_content);
            time = v.findViewById(R.id.tv_ai_time);
        }
    }

    static class UserHolder extends RecyclerView.ViewHolder {
        TextView content, time;
        UserHolder(View v) {
            super(v);
            content = v.findViewById(R.id.tv_user_content);
            time = v.findViewById(R.id.tv_user_time);
        }
    }

    static class SystemHolder extends RecyclerView.ViewHolder {
        SystemHolder(View v) { super(v); }
        void setText(String text) { ((TextView) itemView).setText(text); }
    }

    static class LoadingHolder extends RecyclerView.ViewHolder {
        LoadingHolder(View v) { super(v); }
    }
}
