package com.example.caiyunweather.api;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class WeatherService {
    private static final String CAIYUN_BASE_URL = "https://api.caiyunapp.com/";
    private static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com/";
    private static WeatherService instance;
    private CaiyunWeatherApi caiyunApi;
    private DeepSeekApi deepSeekApi;
    
    private WeatherService() {
        // 创建日志拦截器
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        
        // 创建OkHttpClient并设置超时时间
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)  // 连接超时时间
                .readTimeout(30, TimeUnit.SECONDS)     // 读取超时时间
                .writeTimeout(30, TimeUnit.SECONDS)    // 写入超时时间
                .build();
        
        // 为DeepSeek API创建单独的配置，增加超时时间并优化HTTP/2设置
        OkHttpClient deepSeekClient = new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(120, TimeUnit.SECONDS)   // 连接超时时间增加到120秒
                .readTimeout(120, TimeUnit.SECONDS)      // 读取超时时间增加到120秒
                .writeTimeout(120, TimeUnit.SECONDS)     // 写入超时时间增加到120秒
                // 优化HTTP/2设置
                .retryOnConnectionFailure(true)          // 连接失败时重试
                .followRedirects(true)                   // 跟随重定向
                .followSslRedirects(true)                // 跟随SSL重定向
                .build();
        
        // 创建彩云天气Retrofit实例
        Retrofit caiyunRetrofit = new Retrofit.Builder()
                .baseUrl(CAIYUN_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        
        // 创建DeepSeek Retrofit实例，使用更长的超时时间和优化的配置
        Retrofit deepSeekRetrofit = new Retrofit.Builder()
                .baseUrl(DEEPSEEK_BASE_URL)
                .client(deepSeekClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        
        caiyunApi = caiyunRetrofit.create(CaiyunWeatherApi.class);
        deepSeekApi = deepSeekRetrofit.create(DeepSeekApi.class);
    }
    
    public static synchronized WeatherService getInstance() {
        if (instance == null) {
            instance = new WeatherService();
        }
        return instance;
    }
    
    public CaiyunWeatherApi getCaiyunApi() {
        return caiyunApi;
    }
    
    public DeepSeekApi getDeepSeekApi() {
        return deepSeekApi;
    }
}