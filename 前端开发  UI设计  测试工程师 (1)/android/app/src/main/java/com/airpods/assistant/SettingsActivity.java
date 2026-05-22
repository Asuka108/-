package com.airpods.assistant;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private TextView tvLangValue;
    private String currentLang; // "zh" or "en"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        currentLang = prefs.getString("language", "zh");

        tvLangValue = findViewById(R.id.tv_lang_value);
        updateLangDisplay();

        // 返回
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // 语言切换
        findViewById(R.id.row_language).setOnClickListener(v -> toggleLanguage());

        // 退出登录
        findViewById(R.id.btn_logout).setOnClickListener(v -> logout());
    }

    private void updateLangDisplay() {
        tvLangValue.setText("zh".equals(currentLang)
                ? getString(R.string.settings_language_zh)
                : getString(R.string.settings_language_en));
    }

    private void toggleLanguage() {
        String newLang = "zh".equals(currentLang) ? "en" : "zh";
        currentLang = newLang;

        // 保存语言设置
        getSharedPreferences("settings", MODE_PRIVATE)
                .edit()
                .putString("language", newLang)
                .apply();

        // 立即应用
        setAppLocale(newLang);
        updateLangDisplay();

        Toast.makeText(this, getString(R.string.settings_lang_changed), Toast.LENGTH_SHORT).show();
    }

    private void setAppLocale(String lang) {
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Resources res = getResources();
        Configuration config = res.getConfiguration();
        config.setLocale(locale);
        res.updateConfiguration(config, res.getDisplayMetrics());
    }

    private void logout() {
        getSharedPreferences("auth", MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
        com.airpods.assistant.api.ApiClient.getInstance().setToken(null);

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
