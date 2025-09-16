package com.example.caiyunweather.api;

import com.example.caiyunweather.model.WeatherResponse;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface CaiyunWeatherApi {
    /**
     * 获取天气预报数据
     * @param token API token
     * @param longitude 经度
     * @param latitude 纬度
     * @return 天气预报数据
     */
    @GET("v2.5/{token}/{longitude},{latitude}/weather.json")
    Call<WeatherResponse> getWeatherForecast(
            @Path("token") String token,
            @Path("longitude") double longitude,
            @Path("latitude") double latitude
    );
    
    /**
     * 获取天气预报数据（原始响应）
     * @param token API token
     * @param longitude 经度
     * @param latitude 纬度
     * @return 天气预报数据原始响应
     */
    @GET("v2.5/{token}/{longitude},{latitude}/weather.json")
    Call<ResponseBody> getWeatherForecastRaw(
            @Path("token") String token,
            @Path("longitude") double longitude,
            @Path("latitude") double latitude
    );
}