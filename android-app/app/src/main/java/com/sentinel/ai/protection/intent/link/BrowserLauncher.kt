package com.sentinel.ai.protection.intent.link

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log

/**
 * Utility for launching a browser after a URL has been approved by future protection logic.
 */
class BrowserLauncher {

    /**
     * Opens an approved HTTP(S) URL in Chrome. If Chrome is unavailable, an external-only
     * chooser is shown instead.
     *
     * @return true when Android accepted a browser handoff, false when no other app can handle
     * the URL or the value is not a web URL.
     */
    fun launch(context: Context, url: String): Boolean {
        if (!url.startsWith(HTTP_PREFIX, ignoreCase = true) &&
            !url.startsWith(HTTPS_PREFIX, ignoreCase = true)
        ) {
            Log.w(TAG, "Refusing to launch a non-web URL")
            return false
        }

        val uri = Uri.parse(url)
        if (uri.host.isNullOrBlank()) {
            Log.w(TAG, "Refusing to launch a non-web URL")
            return false
        }

        val viewIntent = Intent(Intent.ACTION_VIEW, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .setPackage(CHROME_PACKAGE)

        return runCatching {
            context.startActivity(viewIntent)
            true
        }.getOrElse { error ->
            Log.i(TAG, "Chrome is unavailable; showing external browser chooser", error)
            launchExternalChooser(context, viewIntent)
        }
    }

    private fun launchExternalChooser(context: Context, intent: Intent): Boolean {
        intent.setPackage(null)
        val handlers = context.packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        val externalHandlers = handlers.filter { it.activityInfo.packageName != context.packageName }
        if (externalHandlers.isEmpty()) {
            Log.w(TAG, "No external browser available for approved URL")
            return false
        }
        val sentinelComponents = handlers
            .asSequence()
            .filter { it.activityInfo.packageName == context.packageName }
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
            .toList()

        return runCatching {
            val chooser = Intent.createChooser(intent, "Open with").apply {
                putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, sentinelComponents.toTypedArray())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        }.getOrElse { error ->
            Log.w(TAG, "No browser available for approved URL", error)
            false
        }
    }

    private companion object {
        const val HTTP_PREFIX = "http://"
        const val HTTPS_PREFIX = "https://"
        const val CHROME_PACKAGE = "com.android.chrome"
        const val TAG = "BrowserLauncher"
    }
}
