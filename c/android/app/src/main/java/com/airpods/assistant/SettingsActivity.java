package com.airpods.assistant;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // 退出登录
        findViewById(R.id.btn_logout).setOnClickListener(v -> {
            getSharedPreferences("auth", MODE_PRIVATE).edit().clear().apply();
            com.airpods.assistant.api.ApiClient.getInstance().setToken(null);
            com.airpods.assistant.api.ApiClient.getInstance().setUserId(0);
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}
