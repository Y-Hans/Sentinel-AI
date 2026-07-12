package com.sentinel.ai.protection.clipboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ClipboardActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ClipboardMonitor.ACTION_IGNORE) {
            ClipboardMonitor.cancelPrompt(context)
        }
    }
}
