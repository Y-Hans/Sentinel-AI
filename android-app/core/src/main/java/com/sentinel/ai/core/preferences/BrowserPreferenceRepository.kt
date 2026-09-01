package com.sentinel.ai.core.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrowserPreferenceRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Gets the preferred browser package name.
     * Returns null if the user selected "Ask every time" or hasn't selected anything yet.
     */
    fun getPreferredBrowser(): String? {
        val browser = prefs.getString(KEY_PREFERRED_BROWSER, null)
        return if (browser == ASK_EVERY_TIME) null else browser
    }

    /**
     * Sets the preferred browser package name.
     * Pass null or "ASK_EVERY_TIME" to prompt the user every time.
     */
    fun setPreferredBrowser(packageName: String?) {
        val valueToSave = packageName ?: ASK_EVERY_TIME
        prefs.edit().putString(KEY_PREFERRED_BROWSER, valueToSave).apply()
    }

    /**
     * Gets the raw preference value for UI purposes.
     */
    fun getRawPreference(): String {
        return prefs.getString(KEY_PREFERRED_BROWSER, ASK_EVERY_TIME) ?: ASK_EVERY_TIME
    }

    companion object {
        private const val PREFS_NAME = "sentinel_browser_prefs"
        private const val KEY_PREFERRED_BROWSER = "preferred_browser"
        const val ASK_EVERY_TIME = "ASK_EVERY_TIME"
    }
}
