package com.sentinel.ai.core.browser

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.sentinel.ai.core.preferences.BrowserPreferenceRepository
import javax.inject.Inject
import javax.inject.Singleton

data class BrowserOption(val packageName: String, val label: String)

@Singleton
class BrowserSelectionPolicy @Inject constructor(
    private val preferenceRepository: BrowserPreferenceRepository
) {
    fun selectBrowserIntent(context: Context, url: String): Intent? {
        val uri = Uri.parse(url)
        if (uri.scheme !in WEB_SCHEMES || uri.host.isNullOrBlank()) return null

        val baseIntent = webIntent(uri)
        val preferredPackage = preferenceRepository.getPreferredBrowser()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        if (preferredPackage != null) {
            val preferredIntent = Intent(baseIntent).setPackage(preferredPackage)
            if (context.packageManager.resolveActivity(preferredIntent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                return preferredIntent
            }
        }

        return createChooserIntent(context, baseIntent)
    }

    fun availableBrowsers(context: Context): List<BrowserOption> {
        val http = queryBrowserPackages(context, Uri.parse("http://example.com"))
        val https = queryBrowserPackages(context, Uri.parse("https://example.com"))
        val browserPackages = http.intersect(https).filter { it != context.packageName }

        return browserPackages.mapNotNull { packageName ->
            runCatching {
                val info = context.packageManager.getApplicationInfo(packageName, 0)
                BrowserOption(packageName, context.packageManager.getApplicationLabel(info).toString())
            }.getOrNull()
        }.sortedBy { it.label.lowercase() }
    }

    private fun createChooserIntent(context: Context, intent: Intent): Intent? {
        val handlers = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val externalHandlers = handlers.filter { it.activityInfo?.packageName != context.packageName }
        if (externalHandlers.isEmpty()) return null

        val ownComponents = handlers
            .filter { it.activityInfo?.packageName == context.packageName }
            .map { android.content.ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
            .toTypedArray()

        return Intent.createChooser(Intent(intent).setPackage(null), "Open with").apply {
            putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, ownComponents)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun queryBrowserPackages(context: Context, uri: Uri): Set<String> =
        context.packageManager.queryIntentActivities(webIntent(uri), PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()

    private fun webIntent(uri: Uri) = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)

    private companion object {
        val WEB_SCHEMES = setOf("http", "https")
    }
}
