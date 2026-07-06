package com.sentinel.ai.core.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit

/**
 * Placeholder for shared API client configuration.
 */
class ApiClient(
    val okHttpClient: OkHttpClient,
    val retrofit: Retrofit
)
