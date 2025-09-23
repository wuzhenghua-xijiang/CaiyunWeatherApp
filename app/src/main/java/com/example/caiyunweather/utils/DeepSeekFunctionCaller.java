package com.example.caiyunweather.utils;

import android.content.Context;
import android.util.Log;

import com.example.caiyunweather.api.WeatherService;
import com.example.caiyunweather.model.WeatherResponse;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Call;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Callback;
import retrofit2.Response;

public class DeepSeekFunctionCaller {
    private static final String DEEPSEEK_API_KEY = "YOUR_DEEPSEEK_API_KEY"; // 请替换为您的DeepSeek API密钥
    private static final String FUNCTION_NAME = "get_caiyun_weather"; // 保持下划线命名以匹配实际函数
    private static final String MCP_FUNCTION_NAME = "get_weather_forecast"; // MCP模式下的函数名
    
    // 添加上下文引用，用于获取API密钥
    private static Context appContext;
    
    public interface WeatherCallback {
        void onSuccess(String weatherData);
        void onError(String error);
    }
    
    /**
     * 设置应用上下文
     * @param context 应用上下文
     */
    public static void setAppContext(Context context) {
        appContext = context.getApplicationContext();
    }
    
    /**
     * 获取DeepSeek API密钥
     * @return API密钥
     */
    private static String getApiKey() {
        if (appContext != null) {
            String key = ApiKeyManager.getInstance(appContext).getDeepSeekApiKey();
            if (key != null && !key.isEmpty() && !key.equals("YOUR_DEEPSEEK_API_KEY")) {
                return key;
            }
        }
        return DEEPSEEK_API_KEY;
    }

    /**
     * 通过DeepSeek Function Calling获取天气预报
     * @param location 位置信息，例如"北京"
     * @param callback 回调接口
     */
    public static void getWeatherForecast(String location, WeatherCallback callback) {
        getWeatherForecastWithRetry(location, callback, 8, 0); // 最多重试8次
    }
    
    /**
     * 通过DeepSeek Function Calling获取天气预报(MCP模式)
     * @param location 位置信息，例如"北京"
     * @param useMcp 是否使用MCP模式
     * @param callback 回调接口
     */
    public static void getWeatherForecast(String location, boolean useMcp, WeatherCallback callback) {
        if (useMcp) {
            getWeatherForecastWithMcp(location, callback);
        } else {
            getWeatherForecastWithRetry(location, callback, 8, 0); // 最多重试8次
        }
    }
    
    /**
     * 通过MCP模式获取天气预报
     */
    private static void getWeatherForecastWithMcp(String location, WeatherCallback callback) {
        // 首先获取MCP服务器的工具列表
        McpClient mcpClient = McpClient.getInstance();
        mcpClient.listTools().thenAccept(toolsResponse -> {
            if (toolsResponse.has("error")) {
                callback.onError("获取MCP工具列表失败: " + toolsResponse.toString());
                return;
            }
            
            // 创建请求体
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", "deepseek-chat");
            requestBody.add("messages", createMessages(location, true));
            requestBody.add("tools", createMcpFunctionsFromResponse(toolsResponse));  // 使用从MCP服务器获取的工具列表
            requestBody.addProperty("temperature", 0.0);
            
            // 将JsonObject转换为RequestBody
            String json = requestBody.toString();
            RequestBody body = RequestBody.create(MediaType.parse("application/json"), json);
            
            // 创建OkHttpClient实例并配置超时时间
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(120, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .writeTimeout(120, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build();
            
            // 创建请求
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url("https://api.deepseek.com/chat/completions")
                    .post(body)
                    .addHeader("Authorization", "Bearer " + getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .build();
            
            // 发送异步请求
            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, IOException e) {
                    // 特别处理网络超时错误
                    if (e instanceof java.net.SocketTimeoutException) {
                        callback.onError("DeepSeek API请求超时，请检查网络连接或稍后重试");
                    } else if (e instanceof java.net.UnknownHostException) {
                        callback.onError("无法连接到DeepSeek API，请检查网络设置");
                    } else {
                        callback.onError("DeepSeek API调用失败: " + e.getMessage());
                    }
                }
                
                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            String responseBody = response.body().string();
                            handleMcpFunctionCallResponse(responseBody, callback);
                        } catch (Exception e) {
                            callback.onError("解析响应失败: " + e.getMessage());
                        }
                    } else {
                        String errorBody = "";
                        if (response.body() != null) {
                            try {
                                errorBody = response.body().string();
                            } catch (IOException e) {
                                // 忽略读取错误体的异常
                            }
                        }
                        
                        // 特别处理429错误（API配额用完）
                        if (response.code() == 429) {
                            callback.onError("DeepSeek API调用失败：API配额已用完，请稍后再试");
                        } else {
                            callback.onError("DeepSeek API调用失败，状态码: " + response.code() + "，错误信息: " + errorBody);
                        }
                    }
                }
            });
        }).exceptionally(throwable -> {
            callback.onError("获取MCP工具列表失败: " + throwable.getMessage());
            return null;
        });
    }
    
    /**
     * 处理MCP Function Calling响应
     */
    private static void handleMcpFunctionCallResponse(String response, WeatherCallback callback) {
        try {
            JsonElement responseElement;
            try {
                Gson gson = new Gson();
                responseElement = gson.fromJson(response, JsonElement.class);
            } catch (Exception e) {
                callback.onError("解析响应失败: " + e.getMessage());
                return;
            }
            
            if (!responseElement.isJsonObject()) {
                callback.onError("响应不是有效的JSON对象");
                return;
            }
            JsonObject responseObject = responseElement.getAsJsonObject();
            JsonArray choices = responseObject.getAsJsonArray("choices");
            
            if (choices == null || choices.size() == 0) {
                callback.onError("响应中没有选择项");
                return;
            }
            
            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonObject message = choice.getAsJsonObject("message");

            // 检查是否有工具调用
            if (message.has("tool_calls") && !message.get("tool_calls").isJsonNull()) {
                JsonArray toolCalls = message.getAsJsonArray("tool_calls");
                if (toolCalls.size() > 0) {
                    JsonObject toolCall = toolCalls.get(0).getAsJsonObject();
                    JsonObject function = toolCall.getAsJsonObject("function");
                    String functionName = function.get("name").getAsString();

                    if (MCP_FUNCTION_NAME.equals(functionName)) {
                        // 从参数中获取位置信息
                        String location = "北京"; // 默认位置
                        // 解析arguments字符串
                        if (function.has("arguments")) {
                            try {
                                String argumentsStr = function.get("arguments").getAsString();
                                JsonObject arguments = new JsonParser().parse(argumentsStr).getAsJsonObject();
                                if (arguments.has("location")) {
                                    location = arguments.get("location").getAsString();
                                }
                            } catch (Exception e) {
                                Log.e("BBBB", "解析arguments失败", e);
                            }
                        }
                        // 对于MCP模式，我们直接调用MCP服务器获取天气数据
                        getWeatherDataFromMcp(location, callback);
                    } else {
                        callback.onError("未知的函数调用: " + functionName);
                    }
                } else {
                    // 直接返回内容
                    String content = message.get("content").getAsString();
                    callback.onSuccess(content);
                }
            } else if (message.has("function_call")) {
                // 兼容旧版本的function_call
                JsonObject functionCall = message.getAsJsonObject("function_call");
                String functionName = functionCall.get("name").getAsString();

                if (MCP_FUNCTION_NAME.equals(functionName)) {
                    // 从参数中获取位置信息
                    String location = "北京"; // 默认位置
                    // 解析arguments字符串
                    if (functionCall.has("arguments")) {
                        try {
                            String argumentsStr = functionCall.get("arguments").getAsString();
                            JsonObject arguments = new JsonParser().parse(argumentsStr).getAsJsonObject();
                            if (arguments.has("location")) {
                                location = arguments.get("location").getAsString();
                            }
                        } catch (Exception e) {
                            Log.e("BBBB", "解析arguments失败", e);
                        }
                    }
                    // 对于MCP模式，我们直接调用MCP服务器获取天气数据
                    getWeatherDataFromMcp(location, callback);
                } else {
                    callback.onError("未知的函数调用: " + functionName);
                }
            } else {
                // 直接返回内容
                String content = message.get("content").getAsString();
                callback.onSuccess(content);
            }

        } catch (Exception e) {
            callback.onError("解析响应失败: " + e.getMessage());
        }
    }
    
    /**
     * 调用MCP服务器获取天气数据 (这个方法应该由AI模型通过MCP协议调用，而不是在Java代码中直接调用)
     */
    private static void getWeatherDataFromMcp(String location, WeatherCallback callback) {
        try {
            // 创建MCP客户端
            McpClient mcpClient = McpClient.getInstance();
            
            // 初始化MCP连接
            mcpClient.initialize().thenCompose(initResponse -> {
                if (initResponse.has("error")) {
                    throw new RuntimeException("MCP初始化失败: " + initResponse.toString());
                }
                
                // 调用天气预报工具
                JsonObject arguments = new JsonObject();
                arguments.addProperty("location", location);
                return mcpClient.callTool("get_weather_forecast", arguments);
            }).thenAccept(weatherResponse -> {
                if (weatherResponse.has("error")) {
                    callback.onError("获取天气数据失败: " + weatherResponse.toString());
                } else {
                    // 提取天气数据
                    String weatherData = weatherResponse.toString();
                    callback.onSuccess(weatherData);
                }
            }).exceptionally(throwable -> {
                callback.onError("获取天气数据失败: " + throwable.getMessage());
                return null;
            });
        } catch (Exception e) {
            callback.onError("调用MCP服务器失败: " + e.getMessage());
        }
    }
    
    /**
     * 带重试机制的天气预报获取方法
     * @param location 位置信息
     * @param callback 回调接口
     * @param maxRetries 最大重试次数
     * @param retryCount 当前重试次数
     */
    private static void getWeatherForecastWithRetry(String location, WeatherCallback callback, int maxRetries, int retryCount) {
        
        // 创建请求体
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", "deepseek-chat");
        requestBody.add("messages", createMessages(location, false));
        requestBody.add("tools", createFunctions());  // 使用tools而不是functions
        requestBody.addProperty("temperature", 0.0);
        
        // 将JsonObject转换为RequestBody
        String json = requestBody.toString();
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), json);
        
        // 创建OkHttpClient实例并配置超时时间
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
        
        // 创建请求
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url("https://api.deepseek.com/chat/completions")
                .post(body)
                .addHeader("Authorization", "Bearer " + getApiKey())
                .addHeader("Content-Type", "application/json")
                .build();
        
        // 发送异步请求
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                // 特别处理网络超时错误
                if (e instanceof java.net.SocketTimeoutException) {
                    if (retryCount < maxRetries) {
                        retryCallWithDelay(location, callback, maxRetries, retryCount + 1);
                    } else {
                        callback.onError("DeepSeek API请求超时，请检查网络连接或稍后重试");
                    }
                } else if (e instanceof java.net.UnknownHostException) {
                    if (retryCount < maxRetries) {
                        retryCallWithDelay(location, callback, maxRetries, retryCount + 1);
                    } else {
                        callback.onError("无法连接到DeepSeek API，请检查网络设置");
                    }
                } else {
                    if (retryCount < maxRetries) {
                        retryCallWithDelay(location, callback, maxRetries, retryCount + 1);
                    } else {
                        callback.onError("DeepSeek API调用失败: " + e.getMessage());
                    }
                }
            }
            
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String responseBody = response.body().string();
                        handleFunctionCallResponse(responseBody, callback);
                    } catch (Exception e) {
                        if (retryCount < maxRetries) {
                            retryCallWithDelay(location, callback, maxRetries, retryCount + 1);
                        } else {
                            callback.onError("解析响应失败: " + e.getMessage());
                        }
                    }
                } else {
                    String errorBody = "";
                    if (response.body() != null) {
                        try {
                            errorBody = response.body().string();
                        } catch (IOException e) {
                            // 忽略读取错误体的异常
                        }
                    }
                    
                    // 特别处理429错误（API配额用完）
                    if (response.code() == 429) {
                        if (retryCount < maxRetries) {
                            retryCallWithDelay(location, callback, maxRetries, retryCount + 1);
                        } else {
                            callback.onError("DeepSeek API调用失败：API配额已用完，请稍后再试");
                        }
                    } else {
                        if (retryCount < maxRetries) {
                            retryCallWithDelay(location, callback, maxRetries, retryCount + 1);
                        } else {
                            callback.onError("DeepSeek API调用失败，状态码: " + response.code() + "，错误信息: " + errorBody);
                        }
                    }
                }
            }
        });
    }
    
    /**
     * 延迟后重试调用
     */
    private static void retryCallWithDelay(String location, WeatherCallback callback, int maxRetries, int retryCount) {
        // 延迟重试，每次重试增加延迟时间（指数退避）
        int delayMillis = (int) (2000 * Math.pow(2, retryCount)); // 2秒, 4秒, 8秒, 16秒, 32秒...
        // 最大延迟不超过120秒
        delayMillis = Math.min(delayMillis, 120000);
        
        // 在主线程中延迟执行重试
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            getWeatherForecastWithRetry(location, callback, maxRetries, retryCount);
        }, delayMillis);
    }
    
    /**
     * 处理Function Calling响应
     */
    private static void handleFunctionCallResponse(String response, WeatherCallback callback) {
        try {
            JsonElement responseElement;
            try {
                Gson gson = new Gson();
                responseElement = gson.fromJson(response, JsonElement.class);
            } catch (Exception e) {
                // 确保在主线程中调用回调
                new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError("解析响应失败: " + e.getMessage());
                    }
                });
                return;
            }
            
            if (!responseElement.isJsonObject()) {
                // 确保在主线程中调用回调
                new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError("响应不是有效的JSON对象");
                    }
                });
                return;
            }
            JsonObject responseObject = responseElement.getAsJsonObject();
            JsonArray choices = responseObject.getAsJsonArray("choices");
            
            if (choices == null || choices.size() == 0) {
                // 确保在主线程中调用回调
                new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError("响应中没有选择项");
                    }
                });
                return;
            }
            
            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonObject message = choice.getAsJsonObject("message");

            // 检查是否有工具调用
            if (message.has("tool_calls") && !message.get("tool_calls").isJsonNull()) {
                JsonArray toolCalls = message.getAsJsonArray("tool_calls");
                if (toolCalls.size() > 0) {
                    JsonObject toolCall = toolCalls.get(0).getAsJsonObject();
                    JsonObject function = toolCall.getAsJsonObject("function");
                    String functionName = function.get("name").getAsString();

                    if (FUNCTION_NAME.equals(functionName)) {
                        // 调用彩云天气API获取真实数据
                        getCaiyunWeatherData(callback);
                    } else {
                        // 确保在主线程中调用回调
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onError("未知的函数调用: " + functionName);
                            }
                        });
                    }
                } else {
                    // 直接返回内容
                    String content = message.get("content").getAsString();
                    // 确保在主线程中调用回调
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(content);
                        }
                    });
                }
            } else if (message.has("function_call")) {
                // 兼容旧版本的function_call
                JsonObject functionCall = message.getAsJsonObject("function_call");
                String functionName = functionCall.get("name").getAsString();

                if (FUNCTION_NAME.equals(functionName)) {
                    // 调用彩云天气API获取真实数据
                    getCaiyunWeatherData(callback);
                } else {
                    // 确保在主线程中调用回调
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError("未知的函数调用: " + functionName);
                        }
                    });
                }
            } else {
                // 直接返回内容
                String content = message.get("content").getAsString();
                // 确保在主线程中调用回调
                new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess(content);
                    }
                });
            }
        } catch (Exception e) {
            // 确保在主线程中调用回调
            new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    callback.onError("解析响应失败: " + e.getMessage());
                }
            });
        }
    }
    
    /**
     * 调用彩云天气API获取真实数据
     */
    private static void getCaiyunWeatherData(WeatherCallback callback) {
        // 获取位置信息（这里使用北京的经纬度作为示例）
        double longitude = 116.4074; // 北京经度
        double latitude = 39.9042;   // 北京纬度
        String token = "YOUR_CAIYUN_WEATHER_TOKEN"; // 彩云天气免费token
        
        // 如果应用上下文可用，尝试从配置文件中获取token
        if (appContext != null) {
            String configToken = ApiKeyManager.getInstance(appContext).getCaiyunWeatherToken();
            if (configToken != null && !configToken.isEmpty() && !configToken.equals("YOUR_CAIYUN_WEATHER_TOKEN")) {
                token = configToken;
            }
        }
        
        // 调用彩云天气API
        Call<ResponseBody> call = WeatherService.getInstance().getCaiyunApi()
                .getWeatherForecastRaw(token, longitude, latitude);

        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, retrofit2.Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String responseBody = response.body().string();
                        
                        // 解析响应
                        Gson gson = new Gson();
                        WeatherResponse weatherResponse = gson.fromJson(responseBody, WeatherResponse.class);
                        
                        // 将WeatherResponse对象转换为JSON字符串
                        String jsonResponse = gson.toJson(weatherResponse);
                        // 确保在主线程中调用回调
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onSuccess(jsonResponse);
                            }
                        });
                    } catch (Exception e) {
                        // 确保在主线程中调用回调
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onError("解析彩云天气API响应失败: " + e.getMessage());
                            }
                        });
                    }
                } else {
                    if (response.errorBody() != null) {
                        try {
                            String errorBody = response.errorBody().string();
                            
                            // 特别处理429错误（API配额用完）
                            if (response.code() == 429) {
                                // 确保在主线程中调用回调
                                new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                                    @Override
                                    public void run() {
                                        callback.onError("彩云天气API调用失败：API配额已用完，请稍后再试或使用付费token");
                                    }
                                });
                            } else {
                                // 确保在主线程中调用回调
                                new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                                    @Override
                                    public void run() {
                                        callback.onError("彩云天气API调用失败，状态码: " + response.code() + "，错误信息: " + errorBody);
                                    }
                                });
                            }
                        } catch (Exception e) {
                            // 确保在主线程中调用回调
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onError("彩云天气API调用失败，状态码: " + response.code());
                                }
                            });
                        }
                    } else {
                        // 特别处理429错误（API配额用完）
                        if (response.code() == 429) {
                            // 确保在主线程中调用回调
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onError("彩云天气API调用失败：API配额已用完，请稍后再试或使用付费token");
                                }
                            });
                        } else {
                            // 确保在主线程中调用回调
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onError("彩云天气API调用失败，状态码: " + response.code());
                                }
                            });
                        }
                    }
                }
            }
            
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                // 特别处理网络超时错误
                if (t instanceof java.net.SocketTimeoutException) {
                    // 确保在主线程中调用回调
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError("彩云天气API请求超时，请检查网络连接或稍后重试");
                        }
                    });
                } else if (t instanceof java.net.UnknownHostException) {
                    // 确保在主线程中调用回调
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError("无法连接到彩云天气API，请检查网络设置");
                        }
                    });
                } else {
                    // 确保在主线程中调用回调
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError("彩云天气API调用失败: " + t.getMessage());
                        }
                    });
                }
            }
        });
    }
    
    private static String getWeatherCondition(int hour) {
        // 根据时间模拟天气状况
        if (hour >= 6 && hour <= 18) {
            return "CLEAR_DAY";
        } else if (hour > 18 && hour < 22) {
            return "PARTLY_CLOUDY";
        } else if (hour >= 22 || hour < 6) {
            return "THUNDERSTORM";
        } else {
            return "RAIN";
        }
    }
    
    /**
     * 根据MCP服务器响应创建MCP函数定义
     */
    private static JsonArray createMcpFunctionsFromResponse(JsonObject toolsResponse) {
        JsonArray functions = new JsonArray();
        
        try {
            // 从响应中提取工具列表
            if (toolsResponse.has("result") && toolsResponse.getAsJsonObject("result").has("tools")) {
                JsonArray tools = toolsResponse.getAsJsonObject("result").getAsJsonArray("tools");
                
                // 遍历工具列表，创建函数定义
                for (int i = 0; i < tools.size(); i++) {
                    JsonObject tool = tools.get(i).getAsJsonObject();
                    
                    // 创建函数包装对象
                    JsonObject functionWrapper = new JsonObject();
                    functionWrapper.addProperty("type", "function");
                    
                    // 创建实际的函数定义
                    JsonObject function = new JsonObject();
                    function.addProperty("name", tool.has("name") ? tool.get("name").getAsString() : MCP_FUNCTION_NAME);
                    function.addProperty("description", tool.has("description") ? tool.get("description").getAsString() : "通过调用MCP服务器获取指定位置的24小时天气预报");
                    
                    // 处理参数定义
                    if (tool.has("inputSchema")) {
                        function.add("parameters", tool.get("inputSchema"));
                    } else {
                        // 如果没有inputSchema，使用默认参数
                        JsonObject parameters = new JsonObject();
                        parameters.addProperty("type", "object");
                        
                        JsonObject properties = new JsonObject();
                        JsonObject locationProp = new JsonObject();
                        locationProp.addProperty("type", "string");
                        locationProp.addProperty("description", "地理位置，例如：北京、上海等");
                        properties.add("location", locationProp);
                        
                        parameters.add("properties", properties);
                        JsonArray required = new JsonArray();
                        required.add("location");
                        parameters.add("required", required);
                        
                        function.add("parameters", parameters);
                    }
                    
                    // 将函数定义添加到包装对象中
                    functionWrapper.add("function", function);
                    
                    // 将包装对象添加到函数数组中
                    functions.add(functionWrapper);
                }
            } else {
                // 如果无法从响应中提取工具列表，使用默认的函数定义
                return createMcpFunctions();
            }
        } catch (Exception e) {
            // 如果解析过程中出现错误，使用默认的函数定义
            Log.e("DeepSeekFunctionCaller", "解析MCP工具列表失败，使用默认函数定义", e);
            return createMcpFunctions();
        }
        
        return functions;
    }
    
    /**
     * 创建MCP函数定义（默认实现）
     */
    private static JsonArray createMcpFunctions() {
        JsonArray functions = new JsonArray();
        
        // 创建函数包装对象
        JsonObject functionWrapper = new JsonObject();
        functionWrapper.addProperty("type", "function");
        
        // 创建实际的函数定义
        JsonObject function = new JsonObject();
        function.addProperty("name", MCP_FUNCTION_NAME);
        function.addProperty("description", "通过调用MCP服务器获取指定位置的24小时天气预报");
        
        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        
        JsonObject properties = new JsonObject();
        
        JsonObject locationProp = new JsonObject();
        locationProp.addProperty("type", "string");
        locationProp.addProperty("description", "地理位置，例如：北京、上海等");
        properties.add("location", locationProp);
        
        parameters.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add("location");
        parameters.add("required", required);
        
        function.add("parameters", parameters);
        
        // 将函数定义添加到包装对象中
        functionWrapper.add("function", function);
        
        // 将包装对象添加到函数数组中
        functions.add(functionWrapper);
        
        return functions;
    }
    
    /**
     * 创建消息数组
     */
    private static JsonArray createMessages(String location, boolean useMcp) {
        JsonArray messages = new JsonArray();
        
        // 系统消息 - 预设提示词
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        if (useMcp) {
            systemMessage.addProperty("content", "你是一个天气预报助手。当用户询问天气时，请务必使用get_weather_forecast工具来获取天气信息。这个工具会调用MCP服务器获取真实的天气数据。请直接返回从MCP服务器获取的JSON数据，不要将其转换为自然语言描述。");
        } else {
            systemMessage.addProperty("content", "你是一个天气预报助手。当用户询问天气时，请务必使用get_caiyun_weather工具来获取天气信息。这个工具会调用彩云天气API获取真实的天气数据。请直接返回从彩云天气API获取的JSON数据，不要将其转换为自然语言描述。");
        }
        messages.add(systemMessage);
        
        // 用户消息
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", "请告诉我" + location + "未来24小时的天气预报，直接返回JSON数据");
        messages.add(userMessage);
        
        return messages;
    }
    
    /**
     * 创建函数定义
     */
    private static JsonArray createFunctions() {
        JsonArray functions = new JsonArray();
        
        // 创建函数包装对象
        JsonObject functionWrapper = new JsonObject();
        functionWrapper.addProperty("type", "function");
        
        // 创建实际的函数定义
        JsonObject function = new JsonObject();
        function.addProperty("name", FUNCTION_NAME);
        function.addProperty("description", "通过调用彩云天气API获取指定位置的24小时天气预报");
        
        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        
        JsonObject properties = new JsonObject();
        
        JsonObject locationProp = new JsonObject();
        locationProp.addProperty("type", "string");
        locationProp.addProperty("description", "地理位置，例如：北京、上海等");
        properties.add("location", locationProp);
        
        parameters.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add("location");
        parameters.add("required", required);
        
        function.add("parameters", parameters);
        
        // 将函数定义添加到包装对象中
        functionWrapper.add("function", function);
        
        // 将包装对象添加到函数数组中
        functions.add(functionWrapper);
        
        return functions;
    }
}