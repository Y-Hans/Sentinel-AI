package com.sentinel.ai.ui.screens.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sentinel.ai.ui.components.SentinelCard
import com.sentinel.ai.ui.components.SentinelMetricCard
import com.sentinel.ai.ui.components.SentinelPill
import com.sentinel.ai.ui.components.SentinelSectionHeader
import com.sentinel.ai.ui.components.riskColor
import com.sentinel.ai.core.model.RiskLevel

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun AboutScreen(
    appVersion: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "About Sentinel AI",
            style = MaterialTheme.typography.displaySmall
        )
        Text(
            text = "Hackathon project built to help users spot scams early and respond with confidence.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SentinelCard {
            SentinelPill(
                label = "Sentinel AI",
                accent = riskColor(RiskLevel.GREEN)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Mission statement",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Protect people from social engineering by turning risky messages into clear, actionable security guidance.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SentinelSectionHeader(
            title = "Project details",
            subtitle = "A compact view of the app identity and source of this prototype."
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SentinelMetricCard(
                label = "Version",
                value = appVersion,
                accent = riskColor(RiskLevel.GREEN),
                supportingText = "UI build identifier for this release."
            )
            SentinelMetricCard(
                label = "Type",
                value = "Hackathon",
                accent = riskColor(RiskLevel.YELLOW),
                supportingText = "Prototype focused on cybersecurity messaging."
            )
            SentinelMetricCard(
                label = "Focus",
                value = "Scam detection",
                accent = riskColor(RiskLevel.RED),
                supportingText = "Threat triage, guidance, and user safety."
            )
        }

        SentinelSectionHeader(
            title = "Credits",
            subtitle = "Built with the frozen backend and a Compose-first UI layer."
        )
        SentinelCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Android UI and product design",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Sentinel engineering workflow, backend integration, and threat scoring pipeline",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Material 3 and Jetpack Compose",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
