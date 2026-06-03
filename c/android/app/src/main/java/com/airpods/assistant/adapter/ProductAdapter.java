package com.airpods.assistant.adapter;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.airpods.assistant.R;
import com.airpods.assistant.model.Product;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 商品列表 RecyclerView 适配器
 * 支持网格布局展示商品卡片（图片、名称、价格、原价、销量）
 */
public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductHolder> {

    private List<Product> products = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(Product product);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setProducts(List<Product> products) {
        this.products = products != null ? products : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ProductHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_product, parent, false);
        return new ProductHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductHolder holder, int position) {
        Product product = products.get(position);

        // 商品图片
        String imageUrl = product.getImage_url();
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
        holder.tvName.setText(product.getName());

        // 现价
        holder.tvPrice.setText(String.format(Locale.getDefault(), "¥%.2f", product.getPrice()));

        // 原价（有值且高于现价时显示，加删除线）
        if (product.getOriginal_price() > 0 && product.getOriginal_price() > product.getPrice()) {
            holder.tvOriginalPrice.setVisibility(View.VISIBLE);
            holder.tvOriginalPrice.setText(String.format(Locale.getDefault(), "¥%.2f", product.getOriginal_price()));
            holder.tvOriginalPrice.setPaintFlags(
                    holder.tvOriginalPrice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            holder.tvOriginalPrice.setVisibility(View.GONE);
        }

        // 销量
        holder.tvSales.setText("已售" + product.getSales() + "件");

        // 点击事件
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(product);
            }
        });
    }

    @Override
    public int getItemCount() {
        return products.size();
    }

    static class ProductHolder extends RecyclerView.ViewHolder {
        ImageView ivImage;
        TextView tvName, tvPrice, tvOriginalPrice, tvSales;

        ProductHolder(View v) {
            super(v);
            ivImage = v.findViewById(R.id.iv_image);
            tvName = v.findViewById(R.id.tv_name);
            tvPrice = v.findViewById(R.id.tv_price);
            tvOriginalPrice = v.findViewById(R.id.tv_original_price);
            tvSales = v.findViewById(R.id.tv_sales);
        }
    }
}
