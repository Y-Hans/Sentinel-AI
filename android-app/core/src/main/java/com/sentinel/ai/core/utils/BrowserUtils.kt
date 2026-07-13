package com.sentinel.ai.core.utils

import android.content.Context
import android.content.Intent
import android.provider.Settings

object BrowserUtils {

    fun isDefaultBrowser(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse("http://example.com")
        }

        val resolveInfo = context.packageManager.resolveActivity(intent, 0)
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }

    fun openDefaultBrowserSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        context.startActivity(intent)
    }
}