package com.sentinel.ai

import android.app.Application
import com.sentinel.ai.contacts.AndroidContactResolver
import com.sentinel.ai.core.utils.Logger
import com.sentinel.ai.core.event.ThreatJournal
import com.sentinel.ai.core.feature.FeatureManager
import com.sentinel.ai.ui.protection.ProtectionControl
import com.sentinel.ai.ui.util.SenderPresentationResolver
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SentinelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FeatureManager.init(applicationContext)
        Logger.init(isDebug = BuildConfig.DEBUG)
        ThreatJournal.initialize(this)
        ProtectionControl.sync(this)
        SenderPresentationResolver.contactResolverFactory = { ctx -> AndroidContactResolver(ctx) }
    }
}
