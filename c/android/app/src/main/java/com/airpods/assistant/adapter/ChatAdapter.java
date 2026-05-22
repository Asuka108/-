package com.airpods.assistant.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.airpods.assistant.R;
import com.airpods.assistant.model.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天消息 RecyclerView 适配器
 * 支持两种布局：AI 消息（左灰）和用户消息（右蓝）
 */
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_AI = 0;
    private static final int TYPE_USER = 1;
    private static final int TYPE_LOADING = 2;

    private List<Message> messages = new ArrayList<>();
    private boolean showLoading = false;

    public void setMessages(List<Message> messages) {
        this.messages = messages != null ? messages : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void addMessage(Message message) {
        this.messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void setShowLoading(boolean show) {
        if (this.showLoading != show) {
            this.showLoading = show;
            if (show) {
                notifyItemInserted(messages.size());
            } else {
                notifyItemRemoved(messages.size());
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (position < messages.size()) {
            return messages.get(position).isUser() ? TYPE_USER : TYPE_AI;
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
            View v = inflater.inflate(R.layout.item_message_user, parent, false);
            return new UserHolder(v);
        } else if (viewType == TYPE_AI) {
            View v = inflater.inflate(R.layout.item_message_ai, parent, false);
            return new AiHolder(v);
        } else {
            View v = inflater.inflate(R.layout.item_loading, parent, false);
            return new LoadingHolder(v);
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
        }
        // LoadingHolder 不需要 bind 数据
    }

    static class AiHolder extends RecyclerView.ViewHolder {
        TextView content, time;
        AiHolder(View v) {
            super(v);
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

    static class LoadingHolder extends RecyclerView.ViewHolder {
        LoadingHolder(View v) {
            super(v);
        }
    }
}
