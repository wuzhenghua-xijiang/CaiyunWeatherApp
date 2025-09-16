package com.example.caiyunweather.utils;

import android.util.Log;

import com.example.caiyunweather.api.WeatherService;
import com.example.caiyunweather.model.WeatherResponse;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;

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
    private static final String TAG = "DeepSeekFunctionCaller";
    private static final String DEEPSEEK_API_KEY = "sk-f35daf1aab08417d8cf6fed593a0db0a"; // 请替换为您的DeepSeek API密钥
    private static final String FUNCTION_NAME = "get_caiyun_weather"; // 保持下划线命名以匹配实际函数
    
    public interface WeatherCallback {
        void onSuccess(String weatherData);
        void onError(String error);
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
     * 带重试机制的天气预报获取方法
     * @param location 位置信息
     * @param callback 回调接口
     * @param maxRetries 最大重试次数
     * @param retryCount 当前重试次数
     */
    private static void getWeatherForecastWithRetry(String location, WeatherCallback callback, int maxRetries, int retryCount) {
        Log.d(TAG, "getWeatherForecastWithRetry: Attempt " + (retryCount + 1) + "/" + (maxRetries + 1));
        
        // 创建请求体
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", "deepseek-chat");
        requestBody.add("messages", createMessages(location));
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
                .addHeader("Authorization", "Bearer " + DEEPSEEK_API_KEY)
                .addHeader("Content-Type", "application/json")
                .build();
        
        // 发送异步请求
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                Log.e(TAG, "DeepSeek API call failed", e);
                // 特别处理网络超时错误
                if (e instanceof java.net.SocketTimeoutException) {
                    if (retryCount < maxRetries) {
                        Log.d(TAG, "Request timeout, retrying... Attempt " + (retryCount + 1) + "/" + (maxRetries + 1));
                        retryCallWithDelay(location, callback, maxRetries, retryCount + 1);
                    } else {
                        callback.onError("DeepSeek API请求超时，请检查网络连接或稍后重试");
                    }
                } else if (e instanceof java.net.UnknownHostException) {
                    if (retryCount < maxRetries) {
                        Log.d(TAG, "Unknown host, retrying... Attempt " + (retryCount + 1) + "/" + (maxRetries + 1));
                        retryCallWithDelay(location, callback, maxRetries, retryCount + 1);
                    } else {
                        callback.onError("无法连接到DeepSeek API，请检查网络设置");
                    }
                } else {
                    if (retryCount < maxRetries) {
                        Log.d(TAG, "Request failed, retrying... Attempt " + (retryCount + 1) + "/" + (maxRetries + 1));
                        retryCallWithDelay(location, callback, maxRetries, retryCount + 1);
                    } else {
                        callback.onError("DeepSeek API调用失败: " + e.getMessage());
                    }
                }
            }
            
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                Log.d(TAG, "onResponse: getWeatherForecast success = " + response.isSuccessful());
                Log.d(TAG, "onResponse: getWeatherForecast code = " + response.code());
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String responseBody = response.body().string();
                        Log.d(TAG, "onResponse: getWeatherForecast body = " + responseBody);
                        handleFunctionCallResponse(responseBody, callback);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to read response body", e);
                        if (retryCount < maxRetries) {
                            Log.d(TAG, "Retrying... Attempt " + (retryCount + 1) + "/" + (maxRetries + 1));
                            retryCallWithDelay(location, callback, maxRetries, retryCount + 1);
                        } else {
                            callback.onError("解析响应失败: " + e.getMessage());
                        }
                    }
                } else {
                    Log.e(TAG, "DeepSeek API call failed with code: " + response.code());
                    String errorBody = "";
                    if (response.body() != null) {
                        try {
                            errorBody = response.body().string();
                            Log.e(TAG, "DeepSeek API error body: " + errorBody);
                        } catch (IOException e) {
                            Log.e(TAG, "Failed to read error body", e);
                        }
                    }
                    
                    // 特别处理429错误（API配额用完）
                    if (response.code() == 429) {
                        if (retryCount < maxRetries) {
                            Log.d(TAG, "API quota exhausted, retrying... Attempt " + (retryCount + 1) + "/" + (maxRetries + 1));
                            retryCallWithDelay(location, callback, maxRetries, retryCount + 1);
                        } else {
                            callback.onError("DeepSeek API调用失败：API配额已用完，请稍后再试");
                        }
                    } else {
                        if (retryCount < maxRetries) {
                            Log.d(TAG, "Retrying... Attempt " + (retryCount + 1) + "/" + (maxRetries + 1));
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
        Log.d(TAG, "Delaying retry for " + delayMillis + " milliseconds");
        
        // 在主线程中延迟执行重试
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            getWeatherForecastWithRetry(location, callback, maxRetries, retryCount);
        }, delayMillis);
    }
    
    /**
     * 处理Function Calling响应
     */
    private static void handleFunctionCallResponse(String response, WeatherCallback callback) {
        Log.d(TAG, "handleFunctionCallResponse: Starting to process response");
        Log.d(TAG, "handleFunctionCallResponse: response " + response);
        try {
            JsonElement responseElement;
            try {
                Gson gson = new Gson();
                responseElement = gson.fromJson(response, JsonElement.class);
                Log.d(TAG, "handleFunctionCallResponse: Successfully parsed JSON response");
            } catch (Exception e) {
                Log.e(TAG, "handleFunctionCallResponse: Failed to parse JSON response", e);
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
                Log.e(TAG, "handleFunctionCallResponse: Response is not a JSON object");
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
                Log.e(TAG, "handleFunctionCallResponse: No choices in response");
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

            Log.d(TAG, "handleFunctionCallResponse: message " + message);
            // 检查是否有工具调用
            if (message.has("tool_calls") && !message.get("tool_calls").isJsonNull()) {
                JsonArray toolCalls = message.getAsJsonArray("tool_calls");
                if (toolCalls.size() > 0) {
                    JsonObject toolCall = toolCalls.get(0).getAsJsonObject();
                    JsonObject function = toolCall.getAsJsonObject("function");
                    String functionName = function.get("name").getAsString();

                    Log.d(TAG, "handleFunctionCallResponse: toolCall functionName " + functionName);
                    if (FUNCTION_NAME.equals(functionName)) {
                        Log.d(TAG, "handleFunctionCallResponse: Calling getCaiyunWeatherData");
                        // 调用彩云天气API获取真实数据
                        getCaiyunWeatherData(callback);
                    } else {
                        Log.e(TAG, "handleFunctionCallResponse: Unknown function call: " + functionName);
                        // 确保在主线程中调用回调
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onError("未知的函数调用: " + functionName);
                            }
                        });
                    }
                } else {
                    Log.d(TAG, "handleFunctionCallResponse: No tool calls, returning content directly");
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

                Log.d(TAG, "handleFunctionCallResponse: functionName " + functionName);
                if (FUNCTION_NAME.equals(functionName)) {
                    Log.d(TAG, "handleFunctionCallResponse: Calling getCaiyunWeatherData");
                    // 调用彩云天气API获取真实数据
                    getCaiyunWeatherData(callback);
                } else {
                    Log.e(TAG, "handleFunctionCallResponse: Unknown function call: " + functionName);
                    // 确保在主线程中调用回调
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError("未知的函数调用: " + functionName);
                        }
                    });
                }
            } else {
                Log.d(TAG, "handleFunctionCallResponse: No function call, returning content directly");
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
            Log.e(TAG, "Error parsing response", e);
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
        String token = "QcevZCCHjrbDtgsP"; // 彩云天气免费token
        
        Log.d(TAG, "getCaiyunWeatherData: Preparing to call Caiyun Weather API");
        Log.d(TAG, "getCaiyunWeatherData: token=" + token + ", longitude=" + longitude + ", latitude=" + latitude);
        
        // 调用彩云天气API
        Call<ResponseBody> call = WeatherService.getInstance().getCaiyunApi()
                .getWeatherForecastRaw(token, longitude, latitude);

        Log.d(TAG, "getCaiyunWeatherData: Calling Caiyun Weather API");
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, retrofit2.Response<ResponseBody> response) {
                Log.d(TAG, "onResponse: getCaiyunWeatherData success = " + response.isSuccessful());
                Log.d(TAG, "onResponse: getCaiyunWeatherData code = " + response.code());
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String responseBody = response.body().string();
                        Log.d(TAG, "onResponse: getCaiyunWeatherData raw response = " + responseBody);
                        
                        // 解析响应
                        Gson gson = new Gson();
                        WeatherResponse weatherResponse = gson.fromJson(responseBody, WeatherResponse.class);
                        
                        // 将WeatherResponse对象转换为JSON字符串
                        String jsonResponse = gson.toJson(weatherResponse);
                        Log.d(TAG, "onResponse: jsonResponse " + jsonResponse);
                        // 确保在主线程中调用回调
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onSuccess(jsonResponse);
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to parse response body", e);
                        // 确保在主线程中调用回调
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onError("解析彩云天气API响应失败: " + e.getMessage());
                            }
                        });
                    }
                } else {
                    Log.e(TAG, "Caiyun weather API call failed with code: " + response.code());
                    if (response.errorBody() != null) {
                        try {
                            String errorBody = response.errorBody().string();
                            Log.e(TAG, "Caiyun weather API error body: " + errorBody);
                            
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
                            Log.e(TAG, "Failed to read error body", e);
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
                Log.e(TAG, "Caiyun weather API call failed", t);
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
     * 创建消息数组
     */
    private static JsonArray createMessages(String location) {
        JsonArray messages = new JsonArray();
        
        // 系统消息 - 预设提示词
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", "你是一个天气预报助手。当用户询问天气时，请务必使用get_caiyun_weather工具来获取天气信息。这个工具会调用彩云天气API获取真实的天气数据。请直接返回从彩云天气API获取的JSON数据，不要将其转换为自然语言描述。");
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