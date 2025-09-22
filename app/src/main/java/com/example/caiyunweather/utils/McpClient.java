package com.example.caiyunweather.utils;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class McpClient {
    private static final String TAG = "McpClient";
    private static final String MCP_SERVER_URL = "http://127.0.0.1:8080";  // 使用127.0.0.1而不是localhost
    private static McpClient instance;
    private final OkHttpClient client;
    private final Gson gson;
    
    private McpClient() {
        // 配置OkHttpClient以允许明文HTTP通信（仅用于开发环境）
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        gson = new Gson();
    }
    
    public static synchronized McpClient getInstance() {
        if (instance == null) {
            instance = new McpClient();
        }
        return instance;
    }
    
    /**
     * 初始化MCP连接
     */
    public CompletableFuture<JsonObject> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject request = new JsonObject();
                request.addProperty("method", "initialize");
                request.addProperty("jsonrpc", "2.0");
                request.addProperty("id", 1);
                
                return sendRequest(request);
            } catch (Exception e) {
                Log.e(TAG, "初始化MCP连接失败", e);
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * 获取工具列表
     */
    public CompletableFuture<JsonObject> listTools() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject request = new JsonObject();
                request.addProperty("method", "tools/list");
                request.addProperty("jsonrpc", "2.0");
                request.addProperty("id", 2);
                
                return sendRequest(request);
            } catch (Exception e) {
                Log.e(TAG, "获取工具列表失败", e);
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * 调用工具
     */
    public CompletableFuture<JsonObject> callTool(String toolName, JsonObject arguments) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject params = new JsonObject();
                params.addProperty("name", toolName);
                params.add("arguments", arguments);
                
                JsonObject request = new JsonObject();
                request.addProperty("method", "tools/call");
                request.add("params", params);
                request.addProperty("jsonrpc", "2.0");
                request.addProperty("id", 3);
                
                return sendRequest(request);
            } catch (Exception e) {
                Log.e(TAG, "调用工具失败: " + toolName, e);
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * 获取天气预报数据
     */
    public CompletableFuture<JsonObject> getWeatherForecast(String location) {
        Log.d(TAG, "getWeatherForecast: location " + location);
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject arguments = new JsonObject();
                arguments.addProperty("location", location);
                
                return callTool("get_weather_forecast", arguments).get();
            } catch (Exception e) {
                Log.e(TAG, "获取天气预报数据失败", e);
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * 获取AI天气建议
     */
    public CompletableFuture<JsonObject> getAiWeatherAdvice(String weatherData) {
        Log.d(TAG, "getAiWeatherAdvice: weatherData " + weatherData);
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject arguments = new JsonObject();
                arguments.addProperty("weather_data", weatherData);
                
                return callTool("get_ai_weather_advice", arguments).get();
            } catch (Exception e) {
                Log.e(TAG, "获取AI天气建议失败", e);
                throw new RuntimeException(e);
            }
        });
    }
    
    private JsonObject sendRequest(JsonObject requestJson) throws IOException {
        String json = gson.toJson(requestJson);
        RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
        
        Request request = new Request.Builder()
                .url(MCP_SERVER_URL)
                .post(body)
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                // 确保响应体是有效的JSON
                JsonElement jsonElement = new JsonParser().parse(responseBody);
                if (jsonElement.isJsonObject()) {
                    return jsonElement.getAsJsonObject();
                } else {
                    throw new IOException("Response is not a valid JSON object: " + responseBody);
                }
            } else {
                throw new IOException("Request failed with code: " + response.code());
            }
        }
    }
}