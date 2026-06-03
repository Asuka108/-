package com.airpods.assistant;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.airpods.assistant.api.ApiClient;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class LoginActivity extends AppCompatActivity {

    private EditText etUsername, etNickname, etPassword;
    private View btnLogin, layoutRegister, btnRegister;
    private TextView tvToggle, tvError;
    private ProgressBar progress;
    private boolean isRegisterMode = false;

    private Gson gson = new Gson();
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences sp = getSharedPreferences("auth", MODE_PRIVATE);
        String token = sp.getString("token", null);
        if (token != null && !token.isEmpty()) {
            ApiClient.getInstance().setToken(token);
            int userId = sp.getInt("user_id", 0);
            ApiClient.getInstance().setUserId(userId);
            startMain(userId);
            return;
        }

        setContentView(R.layout.activity_login);
        initViews();
    }

    private void initViews() {
        etUsername = findViewById(R.id.et_username);
        etNickname = findViewById(R.id.et_nickname);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        layoutRegister = findViewById(R.id.layout_register);
        btnRegister = findViewById(R.id.btn_register);
        tvToggle = findViewById(R.id.tv_toggle);
        tvError = findViewById(R.id.tv_error);
        progress = findViewById(R.id.progress);

        btnLogin.setOnClickListener(v -> doLogin());
        btnRegister.setOnClickListener(v -> doRegister());
        tvToggle.setOnClickListener(v -> toggleMode());
    }

    private void toggleMode() {
        isRegisterMode = !isRegisterMode;
        layoutRegister.setVisibility(isRegisterMode ? View.VISIBLE : View.GONE);
        tvToggle.setText(isRegisterMode ? "已有账号？直接登录" : "没有账号？点击注册");
        tvError.setVisibility(View.GONE);
    }

    private void doLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty()) { showError("请输入用户名"); return; }
        if (password.isEmpty()) { showError("请输入密码"); return; }

        setLoading(true);
        tvError.setVisibility(View.GONE);

        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);

        ApiClient.getInstance().post("/auth/login", body, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                handler.post(() -> { setLoading(false); showError("网络连接失败，请检查网络"); });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                handler.post(() -> setLoading(false));
                if (response.code() == 404) {
                    handler.post(() -> { showError("账号不存在，请先注册"); if (!isRegisterMode) toggleMode(); });
                    return;
                }
                if (response.code() == 401) {
                    handler.post(() -> showError("密码错误"));
                    return;
                }
                handleAuthResponse(response);
            }
        });
    }

    private void doRegister() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String nickname = etNickname.getText().toString().trim();

        if (username.isEmpty()) { showError("请输入用户名"); return; }
        if (password.isEmpty()) { showError("请设置密码"); return; }

        setLoading(true);
        tvError.setVisibility(View.GONE);

        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);
        if (!TextUtils.isEmpty(nickname)) body.addProperty("nickname", nickname);

        ApiClient.getInstance().post("/auth/register", body, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                handler.post(() -> { setLoading(false); showError("网络连接失败，请检查网络"); });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                handler.post(() -> setLoading(false));
                if (response.code() == 400) {
                    handler.post(() -> showError("该用户名已被注册"));
                    return;
                }
                handleAuthResponse(response);
            }
        });
    }

    private void handleAuthResponse(Response response) {
        if (response.isSuccessful() && response.body() != null) {
            try {
                String json = response.body().string();
                JsonObject res = gson.fromJson(json, JsonObject.class);
                String token = res.get("token").getAsString();
                JsonObject user = res.getAsJsonObject("user");
                int userId = user.get("id").getAsInt();
                String nickname = user.has("nickname") && !user.get("nickname").isJsonNull()
                        ? user.get("nickname").getAsString() : "";

                ApiClient.getInstance().setToken(token);
                ApiClient.getInstance().setUserId(userId);
                getSharedPreferences("auth", MODE_PRIVATE).edit()
                        .putString("token", token)
                        .putInt("user_id", userId)
                        .putString("nickname", nickname)
                        .apply();
                startMain(userId);
            } catch (Exception e) {
                handler.post(() -> showError("操作失败，请重试"));
            }
        } else {
            handler.post(() -> showError("操作失败，请重试"));
        }
    }

    private void startMain(int userId) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("user_id", userId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
        btnRegister.setEnabled(!loading);
    }
}
