package com.cursosansolis.appclassws.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import retrofit2.converter.scalars.ScalarsConverterFactory






object RetrofitClient {
    private const val BASE_URL = "http://10.0.2.2/ClaseOffLine/api/"

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttp = OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .addConverterFactory(ScalarsConverterFactory.create())

            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    fun absoluteUrl(relative: String): String {
        if (relative.startsWith("http")) return relative
        val root = BASE_URL.substringBeforeLast("api/")
        val clean = relative.removePrefix("../")
        return if (clean.startsWith("api/")) root + clean else BASE_URL + clean
    }
}
