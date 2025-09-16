package com.example.caiyunweather.api;

import okhttp3.ResponseBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Header;

public interface DeepSeekApi {
    @POST("chat/completions")
    Call<ResponseBody> callFunction(
            @Header("Authorization") String authorization,
            @Header("Content-Type") String contentType,
            @Body RequestBody requestBody
    );
}