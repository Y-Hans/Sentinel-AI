package com.sentinel.ai.warning

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.ui.theme.SentinelTheme

class ScamWarningActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()

        val result = intent.toScanResultOrNull() ?: return finish()

        setContent {
            SentinelTheme {
                WarningScreen(result = result)
            }
        }
    }

    companion object {
        fun newIntent(context: Context, result: ScanResult): Intent =
            Intent(context, ScamWarningActivity::class.java)
                .putExtra(EXTRA_ID, result.id)
                .putExtra(EXTRA_SOURCE, result.source)
                .putExtra(EXTRA_RISK_LEVEL, result.riskLevel.name)
                .putExtra(EXTRA_RISK_SCORE, result.riskScore)
                .putExtra(EXTRA_EXPLANATION, result.explanation)
                .putExtra(EXTRA_TIMESTAMP, result.timestamp)
    }
}

@Composable
private fun WarningScreen(result: ScanResult) {
    val model = result.toWarningUiModel()
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = model.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "Risk Level: ${model.riskLevelLabel}", style = MaterialTheme.typography.titleMedium)
                    Text(text = "Risk Score: ${model.riskScore.toInt()}", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "Reasons:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    model.reasons.forEach {
                        Text(
                            text = "* $it",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun Intent.toScanResultOrNull(): ScanResult? {
    val id = getStringExtra(EXTRA_ID) ?: return null
    val source = getStringExtra(EXTRA_SOURCE) ?: return null
    val riskLevelName = getStringExtra(EXTRA_RISK_LEVEL) ?: return null
    val riskScore = getFloatExtra(EXTRA_RISK_SCORE, 0f)
    val explanation = getStringExtra(EXTRA_EXPLANATION).orEmpty()
    val timestamp = getLongExtra(EXTRA_TIMESTAMP, 0L)

    return ScanResult(
        id = id,
        source = source,
        riskLevel = runCatching { RiskLevel.valueOf(riskLevelName) }.getOrNull() ?: return null,
        riskScore = riskScore,
        explanation = explanation,
        timestamp = timestamp
    )
}

private const val EXTRA_ID = "extra_id"
private const val EXTRA_SOURCE = "extra_source"
private const val EXTRA_RISK_LEVEL = "extra_risk_level"
private const val EXTRA_RISK_SCORE = "extra_risk_score"
private const val EXTRA_EXPLANATION = "extra_explanation"
private const val EXTRA_TIMESTAMP = "extra_timestamp"
