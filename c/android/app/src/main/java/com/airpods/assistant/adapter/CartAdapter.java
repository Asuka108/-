package com.airpods.assistant.adapter;

import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.airpods.assistant.R;
import com.airpods.assistant.model.CartItem;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 购物车列表 RecyclerView 适配器
 * 支持全选/单选、数量增减、删除
 */
public class CartAdapter extends RecyclerView.Adapter<CartAdapter.CartHolder> {

    private List<CartItem> items = new ArrayList<>();
    private OnCartItemChangeListener listener;

    public interface OnCartItemChangeListener {
        void onSelectChange(CartItem item, boolean selected);
        void onQuantityChange(CartItem item, int newQuantity);
        void onDelete(CartItem item);
    }

    public void setOnCartItemChangeListener(OnCartItemChangeListener listener) {
        this.listener = listener;
    }

    public void setItems(List<CartItem> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    public List<CartItem> getItems() {
        return items;
    }

    @NonNull
    @Override
    public CartHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cart, parent, false);
        return new CartHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CartHolder holder, int position) {
        CartItem item = items.get(position);

        // 商品图片
        String imageUrl = item.getImage_url();
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(imageUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .centerCrop()
                    .into(holder.ivImage);
        } else {
            holder.ivImage.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        // 商品名称
        holder.tvName.setText(item.getName());

        // 价格
        holder.tvPrice.setText(String.format(Locale.getDefault(), "¥%.2f", item.getPrice()));

        // 数量
        holder.tvQuantity.setText(String.valueOf(item.getQuantity()));

        // 选择状态（先移除监听器，避免复用时触发）
        holder.cbSelect.setOnCheckedChangeListener(null);
        holder.cbSelect.setChecked(item.isSelected());
        holder.cbSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
            item.setSelected(isChecked);
            if (listener != null) {
                listener.onSelectChange(item, isChecked);
            }
        });

        // 减少数量
        holder.btnMinus.setOnClickListener(v -> {
            if (item.getQuantity() <= 1) {
                // 数量为1时点击减号，弹窗确认删除
                new AlertDialog.Builder(holder.itemView.getContext())
                        .setTitle("提示")
                        .setMessage("确定要删除该商品吗？")
                        .setPositiveButton("确定", (dialog, which) -> {
                            if (listener != null) {
                                listener.onDelete(item);
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            } else {
                if (listener != null) {
                    listener.onQuantityChange(item, item.getQuantity() - 1);
                }
            }
        });

        // 增加数量
        holder.btnPlus.setOnClickListener(v -> {
            if (listener != null) {
                listener.onQuantityChange(item, item.getQuantity() + 1);
            }
        });

        // 删除
        holder.btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(holder.itemView.getContext())
                    .setTitle("提示")
                    .setMessage("确定要删除该商品吗？")
                    .setPositiveButton("确定", (dialog, which) -> {
                        if (listener != null) {
                            listener.onDelete(item);
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class CartHolder extends RecyclerView.ViewHolder {
        CheckBox cbSelect;
        ImageView ivImage;
        TextView tvName, tvPrice, tvQuantity, btnMinus, btnPlus, btnDelete;

        CartHolder(View v) {
            super(v);
            cbSelect = v.findViewById(R.id.cb_select);
            ivImage = v.findViewById(R.id.iv_image);
            tvName = v.findViewById(R.id.tv_name);
            tvPrice = v.findViewById(R.id.tv_price);
            tvQuantity = v.findViewById(R.id.tv_quantity);
            btnMinus = v.findViewById(R.id.btn_minus);
            btnPlus = v.findViewById(R.id.btn_plus);
            btnDelete = v.findViewById(R.id.btn_delete);
        }
    }
}
