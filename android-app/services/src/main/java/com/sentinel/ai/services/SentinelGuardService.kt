package com.sentinel.ai.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.sentinel.ai.core.event.ThreatEventBus
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * FUTURE EXTENSION POINT: SentinelGuardService
 * Class: UNUSED BUT INTENTIONAL (Scaffolding)
 *
 * This service is a placeholder for a future persistent foreground guard.
 * It currently does not provide any active security functionality or foreground protection.
 */
@AndroidEntryPoint
class SentinelGuardService : Service() {

    @Inject
    lateinit var threatEventBus: ThreatEventBus

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Placeholder: start foreground notification and subscribe to threat events.
        return START_STICKY
    }
}
