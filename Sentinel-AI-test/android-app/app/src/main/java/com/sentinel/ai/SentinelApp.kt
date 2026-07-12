package com.sentinel.ai

import android.app.Application
import com.sentinel.ai.core.utils.Logger
import android.content.Intent
import com.sentinel.ai.core.event.ThreatJournal
import com.sentinel.ai.warning.ThreatEventSubscriberService
import com.sentinel.ai.ui.protection.ProtectionControl
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SentinelApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Logger.init(isDebug = BuildConfig.DEBUG)
        ThreatJournal.initialize(this)
        ProtectionControl.sync(this)
        startService(Intent(this, ThreatEventSubscriberService::class.java))
    }
}
