package com.cactuvi.app.data.api

import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    private var retrofit: Retrofit? = null

    fun createService(baseUrl: String): XtreamApiService {
        if (retrofit == null || retrofit?.baseUrl().toString() != baseUrl) {
            val loggingInterceptor =
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }

            val headerInterceptor = Interceptor { chain ->
                val originalRequest = chain.request()
                val requestWithHeaders =
                    originalRequest
                        .newBuilder()
                        .addHeader("User-Agent", "IPTV Player Android")
                        .addHeader("Accept", "application/json")
                        .addHeader("Connection", "close")
                        .build()
                chain.proceed(requestWithHeaders)
            }

            val okHttpClient =
                OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .addInterceptor(headerInterceptor)
                    .addInterceptor(loggingInterceptor)
                    .build()

            retrofit =
                Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
        }

        return retrofit!!.create(XtreamApiService::class.java)
    }
}
