package com.airpods.assistant.api;

import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * API 请求客户端
 * 封装 OkHttp，统一处理 token、超时、错误
 */
public class ApiClient {

    private static final String TAG = "ApiClient";

    // 部署后替换为实际服务器地址
    private static String BASE_URL = "http://10.0.2.2:8000/api/v1";

    private OkHttpClient client;
    private Gson gson;
    private String token;

    private static ApiClient instance;
    private int userId;

    public static synchronized ApiClient getInstance() {
        if (instance == null) {
            instance = new ApiClient();
        }
        return instance;
    }

    private ApiClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();
        gson = new Gson();
    }

    public void setBaseUrl(String url) { BASE_URL = url; }
    public String getBaseUrl() { return BASE_URL; }

    public void setToken(String token) { this.token = token; }
    public String getToken() { return token; }

    public void setUserId(int userId) { this.userId = userId; }
    public int getUserId() { return userId; }

    /** GET 请求 */
    public void get(String path, Map<String, String> params, Callback callback) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(BASE_URL + path).newBuilder();
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                urlBuilder.addQueryParameter(e.getKey(), e.getValue());
            }
        }
        Request request = buildRequest("GET", urlBuilder.build().toString(), null);
        client.newCall(request).enqueue(callback);
    }

    /** POST 请求 */
    public void post(String path, Object body, Callback callback) {
        String json = gson.toJson(body);
        Request request = buildRequest("POST", BASE_URL + path, json);
        client.newCall(request).enqueue(callback);
    }

    /** PUT 请求 */
    public void put(String path, Object body, Callback callback) {
        String json = gson.toJson(body);
        Request request = buildRequest("PUT", BASE_URL + path, json);
        client.newCall(request).enqueue(callback);
    }

    /** DELETE 请求 */
    public void delete(String path, Callback callback) {
        Request request = buildRequest("DELETE", BASE_URL + path, null);
        client.newCall(request).enqueue(callback);
    }

    /** 同步 POST（用于简单场景） */
    public String postSync(String path, Object body) throws IOException {
        String json = gson.toJson(body);
        Request request = buildRequest("POST", BASE_URL + path, json);
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            }
            throw new IOException("HTTP " + response.code());
        }
    }

    /** 同步 GET */
    public String getSync(String path) throws IOException {
        Request request = buildRequest("GET", BASE_URL + path, null);
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            }
            throw new IOException("HTTP " + response.code());
        }
    }

    private Request buildRequest(String method, String url, String body) {
        Request.Builder builder = new Request.Builder().url(url);
        if (body != null) {
            builder.post(RequestBody.create(body, MediaType.parse("application/json")));
        } else if ("POST".equals(method) || "PUT".equals(method)) {
            builder.post(RequestBody.create("{}", MediaType.parse("application/json")));
        }
        if ("DELETE".equals(method)) {
            builder.delete();
        }
        if (token != null && !token.isEmpty()) {
            builder.addHeader("Authorization", "Bearer " + token);
        }
        builder.addHeader("Content-Type", "application/json");
        return builder.build();
    }
}
