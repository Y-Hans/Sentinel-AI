package com.sentinel.ai.protection.intent.reputation

// Excluded from compile in Phase 3.7 to keep implementation completely offline.
// 
// import com.sentinel.ai.core.coroutines.DispatcherProvider
// import com.sentinel.ai.protection.intent.link.UrlNormalizer
// import java.net.URI
// import javax.inject.Inject
// import javax.inject.Singleton
// import kotlinx.coroutines.withContext
// import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
// import okhttp3.OkHttpClient
// import okhttp3.Request
// 
// @Singleton
// class OpenPhishReputationProvider @Inject constructor(
//     private val okHttpClient: OkHttpClient,
//     private val dispatcherProvider: DispatcherProvider,
//     private val config: ReputationConfig
// ) : ReputationProvider {
// 
//     override val providerName: String = "OpenPhish"
// 
//     override fun supports(target: ReputationTarget): Boolean = target is ReputationTarget.Url
// 
//     override suspend fun evaluate(target: ReputationTarget): ReputationResult? {
//         val urlTarget = target as? ReputationTarget.Url ?: return null
//         if (!config.isOpenPhishEnabled) {
//             return null
//         }
// 
//         val feedUrl = buildFeedUrl() ?: return null
//         val feedBody = withContext(dispatcherProvider.io) {
//             runCatching {
//                 val request = Request.Builder()
//                     .url(feedUrl)
//                     .apply {
//                         if (config.openPhishApiKey.isNotBlank()) {
//                             header("Authorization", "Bearer ${config.openPhishApiKey}")
//                         }
//                     }
//                     .build()
// 
//                 okHttpClient.newCall(request).execute().use { response ->
//                     if (!response.isSuccessful) {
//                         return@runCatching null
//                     }
// 
//                     response.body?.string()
//                 }
//             }.getOrNull()
//         } ?: return null
// 
//         val normalizedTarget = UrlNormalizer.normalize(urlTarget.url)
//         val isThreat = feedBody
//             .lineSequence()
//             .map { it.trim() }
//             .filter { it.isNotEmpty() && !it.startsWith("#") }
//             .any { entry -> entry.matchesTarget(normalizedTarget) }
// 
//         return ReputationResult(
//             providerName = providerName,
//             confidence = if (isThreat) 0.98f else 0.72f,
//             reputation = if (isThreat) ReputationVerdict.MALICIOUS else ReputationVerdict.CLEAN,
//             reason = if (isThreat) {
//                 "Matched OpenPhish feed entry."
//             } else {
//                 "No OpenPhish feed match detected."
//             },
//             timestamp = System.currentTimeMillis()
//         )
//     }
// 
//     private fun buildFeedUrl(): okhttp3.HttpUrl? {
//         val rawUrl = config.openPhishFeedUrl.trim()
//         if (rawUrl.isBlank()) {
//             return null
//         }
// 
//         val key = config.openPhishApiKey.trim()
//         if (key.isBlank()) {
//             return rawUrl.toHttpUrlOrNull()
//         }
// 
//         return rawUrl.toHttpUrlOrNull()?.newBuilder()
//             ?.addQueryParameter("api_key", key)
//             ?.build()
//             ?: runCatching { URI(rawUrl) }.getOrNull()?.toString()?.toHttpUrlOrNull()
//     }
// 
//     private fun String.matchesTarget(normalizedTarget: String): Boolean {
//         val normalizedEntry = UrlNormalizer.normalize(this)
//         if (normalizedEntry.equals(normalizedTarget, ignoreCase = true)) {
//             return true
//         }
// 
//         val targetHost = runCatching { URI(normalizedTarget).host?.lowercase() }.getOrNull()
//         val entryHost = runCatching { URI(normalizedEntry).host?.lowercase() }.getOrNull()
// 
//         return !targetHost.isNullOrBlank() &&
//             !entryHost.isNullOrBlank() &&
//             targetHost == entryHost
//     }
// }
