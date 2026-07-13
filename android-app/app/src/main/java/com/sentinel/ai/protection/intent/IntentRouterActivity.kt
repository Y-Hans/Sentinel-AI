package com.sentinel.ai.protection.intent

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import com.sentinel.ai.core.feature.FeatureManager
import com.sentinel.ai.protection.intent.model.FilePayload
import com.sentinel.ai.protection.intent.model.IntentPayload
import com.sentinel.ai.protection.intent.model.UrlPayload

/**
 * Entry point for future intent-based protection flows.
 *
 * This activity receives an incoming intent, classifies the payload as a URL or file when
 * possible, forwards the payload to [ScanLoadingActivity], and then finishes without performing
 * any protection work.
 */
class IntentRouterActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!FeatureManager.isClickEnabled()) {
            finish()
            return
        }

        val payload = intent.toIntentPayloadOrNull()
        if (payload == null) {
            Log.i(TAG, "Unsupported intent received: action=${intent.action}")
            finish()
            return
        }

        Log.i(TAG, "Routing payload type: ${payload.payloadTypeLabel}")
        Log.d(ML_TAG, "Router payload class=${payload.javaClass.name}")
        Log.d(ML_TAG, "Router payload=$payload")
        val isViewIntent = intent.action == Intent.ACTION_VIEW
        val scanIntent = Intent(this, ScanLoadingActivity::class.java).apply {
            putExtra(IntentPayloadExtras.EXTRA_PAYLOAD_TYPE, payload.payloadTypeKey)
            putExtra(IntentPayloadExtras.EXTRA_PAYLOAD_VALUE, payload.payloadValue)
            putExtra(IntentPayloadExtras.EXTRA_FROM_VIEW_INTENT, isViewIntent)
        }
        Log.d(
            ML_TAG,
            "Forwarding extras: type=${payload.payloadTypeKey}, value=${payload.payloadValue}"
        )
        if (payload is FilePayload) {
            scanIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            scanIntent.data = payload.uri
        }
        startActivity(scanIntent)
        finish()
    }

    private fun Intent.toIntentPayloadOrNull(): IntentPayload? {
        data?.toIntentPayload()?.let { return it }
        clipData?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.uri
            ?.toIntentPayload()
            ?.let { return it }

        getSharedTextPayload()?.let { return it }
        getSharedStreamPayload()?.let { return it }

        return null
    }

    private fun Uri.toIntentPayload(): IntentPayload? {
        return when (scheme?.lowercase()) {
            "http", "https" -> UrlPayload(url = toString())
            "content", "file" -> FilePayload(uri = this)
            else -> null
        }
    }

    private fun Intent.getSharedTextPayload(): IntentPayload? {
        val sharedText = getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (sharedText.isEmpty()) {
            return null
        }

        return when {
            sharedText.startsWith("http://", ignoreCase = true) ||
                sharedText.startsWith("https://", ignoreCase = true) ->
                UrlPayload(url = sharedText)
            else -> null
        }
    }

    private fun Intent.getSharedStreamPayload(): IntentPayload? {
        val sharedUri: Uri? = clipData?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.uri
            ?: when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                        ?: getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                            ?.firstOrNull()
                }
                else -> {
                    @Suppress("DEPRECATION")
                    getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                }
            }

        return sharedUri?.toIntentPayload()
    }

    private val IntentPayload.payloadTypeLabel: String
        get() = when (this) {
            is UrlPayload -> "URL"
            is FilePayload -> "FILE"
        }

    private val IntentPayload.payloadTypeKey: String
        get() = when (this) {
            is UrlPayload -> IntentPayloadExtras.TYPE_URL
            is FilePayload -> IntentPayloadExtras.TYPE_FILE
        }

    private val IntentPayload.payloadValue: String
        get() = when (this) {
            is UrlPayload -> url
            is FilePayload -> uri.toString()
        }

    private companion object {
        const val TAG = "IntentRouterActivity"
        const val ML_TAG = "ML_DEBUG"
    }
}
