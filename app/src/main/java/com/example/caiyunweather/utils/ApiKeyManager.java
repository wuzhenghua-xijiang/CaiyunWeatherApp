package com.example.caiyunweather.utils;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * API密钥管理器
 */
public class ApiKeyManager {
    private static final String TAG = "ApiKeyManager";
    private static final String PROPERTIES_FILE = "api_keys.properties";
    
    private static ApiKeyManager instance;
    private Properties properties;
    
    private ApiKeyManager(Context context) {
        loadProperties(context);
    }
    
    public static synchronized ApiKeyManager getInstance(Context context) {
        if (instance == null) {
            instance = new ApiKeyManager(context);
        }
        return instance;
    }
    
    private void loadProperties(Context context) {
        properties = new Properties();
        try {
            InputStream inputStream = context.getAssets().open(PROPERTIES_FILE);
            properties.load(inputStream);
            inputStream.close();
        } catch (IOException e) {
            Log.e(TAG, "加载API密钥配置文件失败", e);
        }
    }
    
    /**
     * 获取DeepSeek API密钥
     * @return DeepSeek API密钥
     */
    public String getDeepSeekApiKey() {
        String key = properties.getProperty("deepseek.api.key", "YOUR_DEEPSEEK_API_KEY");
        // 如果配置文件中的值是占位符，返回默认值
        if ("YOUR_DEEPSEEK_API_KEY".equals(key)) {
            return "YOUR_DEEPSEEK_API_KEY";
        }
        return key;
    }
    
    /**
     * 获取彩云天气Token
     * @return 彩云天气Token
     */
    public String getCaiyunWeatherToken() {
        String token = properties.getProperty("caiyun.weather.token", "YOUR_CAIYUN_WEATHER_TOKEN");
        // 如果配置文件中的值是占位符，返回默认值
        if ("YOUR_CAIYUN_WEATHER_TOKEN".equals(token)) {
            return "YOUR_CAIYUN_WEATHER_TOKEN";
        }
        return token;
    }
}