package com.sentinel.ai.core.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit

/**
 * Groups networking dependencies into a single injectable unit.
 *
 * This was created in Phase 3.8.1 to preserve the existing [ApiClient]
 * signature while adding [HttpClientWrapper] capabilities. It contains the
 * shared [OkHttpClient] and [Retrofit] instances used before Phase 3.8.1,
 * and now also exposes [httpClientWrapper] — the primary networking
 * abstraction for API requests.
 *
 * Provided as a Hilt singleton by [com.sentinel.ai.core.di.NetworkModule].
 */
class ApiClient(
    val okHttpClient: OkHttpClient,
    val retrofit: Retrofit,
    val httpClientWrapper: HttpClientWrapper
)
