package com.sentinel.ai.core.browser

import android.content.Context
import android.util.Log
import com.sentinel.ai.core.utils.UrlLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrowserLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val selectionPolicy: BrowserSelectionPolicy
) {
    fun launch(url: String): Boolean {
        val intent = selectionPolicy.selectBrowserIntent(context, url) ?: return false
        return runCatching {
            Log.d(TAG, "Launching approved URL: ${UrlLogger.redactUrl(url)}")
            context.startActivity(intent)
            true
        }.getOrElse {
            Log.w(TAG, "Failed to launch browser intent", it)
            false
        }
    }

    private companion object { const val TAG = "BrowserLauncher" }
}
