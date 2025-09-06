package com.example.aquatrack.api

import com.example.aquatrack.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // Base URL comes from BuildConfig (different for debug/release)
    private val baseUrl: String = BuildConfig.API_BASE_URL

    private val loggingInterceptor: HttpLoggingInterceptor? = try {
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.BASIC
        }
    } catch (_: Throwable) { null }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder().apply {
        loggingInterceptor?.let { addInterceptor(it) }
        connectTimeout(15, TimeUnit.SECONDS)
        readTimeout(30, TimeUnit.SECONDS)
        writeTimeout(30, TimeUnit.SECONDS)
    }.build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: ApiService = retrofit.create(ApiService::class.java)
}
