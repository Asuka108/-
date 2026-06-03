package com.airpods.assistant;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

/**
 * 个人中心界面 Fragment
 * 展示用户信息和功能菜单入口
 */
public class ProfileFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
    }

    private void initViews(View view) {
        // 我的订单
        view.findViewById(R.id.menu_orders).setOnClickListener(v ->
                startActivity(new Intent(getActivity(), OrderListActivity.class)));

        // 购物车
        view.findViewById(R.id.menu_cart).setOnClickListener(v ->
                startActivity(new Intent(getActivity(), CartActivity.class)));

        // 对话历史
        view.findViewById(R.id.menu_history).setOnClickListener(v ->
                startActivity(new Intent(getActivity(), HistoryActivity.class)));

        // 知识库管理（管理员功能）
        view.findViewById(R.id.menu_admin).setOnClickListener(v ->
                startActivity(new Intent(getActivity(), AdminActivity.class)));

        // 关于我们
        view.findViewById(R.id.menu_about).setOnClickListener(v -> showAboutDialog());

        // 设置
        view.findViewById(R.id.menu_settings).setOnClickListener(v ->
                startActivity(new Intent(getActivity(), SettingsActivity.class)));

        // 退出登录
        view.findViewById(R.id.menu_logout).setOnClickListener(v -> {
            requireActivity().getSharedPreferences("auth", 0).edit().clear().apply();
            com.airpods.assistant.api.ApiClient.getInstance().setToken(null);
            com.airpods.assistant.api.ApiClient.getInstance().setUserId(0);
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            requireActivity().finish();
        });
    }

    /** 显示关于对话框 */
    private void showAboutDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("关于我们")
                .setMessage("菠萝耳机商城 v3.1\n菠萝耳机AI售后客服系统\n仅供学习使用")
                .setPositiveButton("确定", null)
                .show();
    }
}
