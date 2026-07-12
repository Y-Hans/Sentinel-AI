package com.sentinel.ai.protection.intent

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import com.sentinel.ai.protection.intent.model.FilePayload
import com.sentinel.ai.protection.intent.model.IntentPayload
import com.sentinel.ai.protection.intent.model.UrlPayload
import java.util.Locale

/**
 * Entry point for intent-based protection flows.
 *
 * This activity receives an incoming intent, classifies the payload as a URL or file when
 * possible, forwards the payload to [ScanLoadingActivity], and then finishes without performing
 * any protection work.
 */
class IntentRouterActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val incomingIntent = intent
        Log.d(TAG, "Incoming intent: action=${incomingIntent.action}, uri=${incomingIntent.data}")

        if (incomingIntent.isInternalLaunch()) {
            Log.d(TAG, "Ignored internal launch")
            finish()
            return
        }

        val payload = runCatching { incomingIntent.toIntentPayloadOrNull() }
            .onFailure { error -> Log.w(TAG, "Ignored malformed intent", error) }
            .getOrNull()
        if (payload == null) {
            Log.d(
                TAG,
                "Ignored intent: action=${incomingIntent.action}, uri=${incomingIntent.data}"
            )
            finish()
            return
        }

        when (payload) {
            is UrlPayload -> Log.d(TAG, "Intercepted URL: ${payload.url}")
            is FilePayload -> Log.d(TAG, "Accepted shared file: ${payload.uri}")
        }

        val isViewIntent = incomingIntent.action == Intent.ACTION_VIEW
        val scanIntent = Intent(this, ScanLoadingActivity::class.java).apply {
            putExtra(IntentPayloadExtras.EXTRA_PAYLOAD_TYPE, payload.payloadTypeKey)
            putExtra(IntentPayloadExtras.EXTRA_PAYLOAD_VALUE, payload.payloadValue)
            putExtra(IntentPayloadExtras.EXTRA_FROM_VIEW_INTENT, isViewIntent)
        }
        if (payload is FilePayload) {
            scanIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            scanIntent.data = payload.uri
        }
        startActivity(scanIntent)
        finish()
    }

    private fun Intent.isInternalLaunch(): Boolean {
        val hasInternalFlag = runCatching {
            getBooleanExtra(IntentPayloadExtras.EXTRA_INTERNAL_LAUNCH, false)
        }.getOrDefault(false)

        return getPackage() == packageName || hasInternalFlag
    }

    private fun Intent.toIntentPayloadOrNull(): IntentPayload? {
        return when (action) {
            Intent.ACTION_VIEW -> data?.toValidatedWebPayloadOrNull()
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> toSharedPayloadOrNull()
            else -> null
        }
    }

    private fun Intent.toSharedPayloadOrNull(): IntentPayload? {
        data?.toSharePayloadOrNull()?.let { return it }

        clipData?.let { sharedClipData ->
            for (index in 0 until sharedClipData.itemCount) {
                val item = sharedClipData.getItemAt(index)
                item.uri?.toSharePayloadOrNull()?.let { return it }
                item.text
                    ?.toString()
                    ?.trim()
                    ?.toValidatedWebPayloadOrNull()
                    ?.let { return it }
            }
        }

        getSharedTextPayload()?.let { return it }
        getSharedStreamPayload()?.let { return it }

        return null
    }

    private fun Uri.toSharePayloadOrNull(): IntentPayload? {
        return when (scheme?.lowercase(Locale.ROOT)) {
            HTTP_SCHEME, HTTPS_SCHEME -> toValidatedWebPayloadOrNull()
            "content", "file" -> FilePayload(uri = this)
            else -> null
        }
    }

    private fun Uri.toValidatedWebPayloadOrNull(): UrlPayload? {
        val normalizedScheme = scheme?.lowercase(Locale.ROOT)
        val validScheme = normalizedScheme == HTTP_SCHEME || normalizedScheme == HTTPS_SCHEME
        val validHost = !host.isNullOrBlank()

        return if (validScheme && validHost) {
            UrlPayload(url = toString())
        } else {
            null
        }
    }

    private fun String.toValidatedWebPayloadOrNull(): UrlPayload? {
        if (isBlank()) {
            return null
        }
        return Uri.parse(this).toValidatedWebPayloadOrNull()
    }

    private fun Intent.getSharedTextPayload(): IntentPayload? {
        return getCharSequenceExtra(Intent.EXTRA_TEXT)
            ?.toString()
            ?.trim()
            ?.toValidatedWebPayloadOrNull()
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

        return sharedUri?.toSharePayloadOrNull()
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
        const val TAG = "IntentRouter"
        const val HTTP_SCHEME = "http"
        const val HTTPS_SCHEME = "https"
    }
}
