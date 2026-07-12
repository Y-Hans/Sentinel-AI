package com.sentinel.ai.protection.intent

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.sentinel.ai.core.feature.FeatureManager

/**
 * Android text-selection entry point for selected web links.
 *
 * Receives ACTION_PROCESS_TEXT from the platform selection menu, extracts the
 * URL (prepending the scheme if missing), and forwards it to the intent scan pipeline.
 */
class TextSelectionProcessActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!FeatureManager.isTextEnabled()) {
            finish()
            return
        }

        val selectedText = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT).orEmpty()
        val extractedUrl = extractUrl(selectedText)

        if (extractedUrl == null) {
            Toast.makeText(
                this,
                "Please select a valid web link.",
                Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }

        val normalizedUrl = extractedUrl.trim()
        startActivity(
            Intent(this, ScanLoadingActivity::class.java).apply {
                putExtra(IntentPayloadExtras.EXTRA_PAYLOAD_TYPE, IntentPayloadExtras.TYPE_URL)
                putExtra(IntentPayloadExtras.EXTRA_PAYLOAD_VALUE, normalizedUrl)
            }
        )
        finish()
    }

    private fun extractUrl(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        // Try direct parsing if it has a scheme
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            val uri = runCatching { Uri.parse(trimmed) }.getOrNull()
            if (uri != null && !uri.host.isNullOrBlank()) {
                return trimmed
            }
        }

        // Search for any URL in the text using standard URL regex
        val urlRegex = Regex("""https?://[^\s/$.?#].[^\s]*""", RegexOption.IGNORE_CASE)
        val matchedUrl = urlRegex.find(trimmed)?.value
        if (matchedUrl != null) {
            return matchedUrl
        }

        // Check if the trimmed text is a domain-like pattern (e.g., domain.tld or sub.domain.tld)
        val domainRegex = Regex("""^[a-zA-Z0-9.-]+\.[a-zA-Z]{2,6}(/.*)?$""")
        if (domainRegex.matches(trimmed)) {
            return "https://$trimmed"
        }

        return null
    }
}
