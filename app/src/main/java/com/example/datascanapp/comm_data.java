package com.example.datascanapp;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface comm_data {

    @Headers("Content-Type: application/json")
    @POST("sensor/opensrc/test/")
    Call<String> post_json(@Body postdata pd);
}