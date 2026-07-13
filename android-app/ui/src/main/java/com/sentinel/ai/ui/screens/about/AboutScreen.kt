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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.ui.components.SentinelCard
import com.sentinel.ai.ui.components.SentinelSectionHeader
import com.sentinel.ai.ui.components.StatisticCard
import com.sentinel.ai.ui.components.ThreatLevelChip
import com.sentinel.ai.ui.components.riskColor
import com.sentinel.ai.ui.theme.SentinelSize
import com.sentinel.ai.ui.theme.SentinelSpacing

private fun riskState(level: RiskLevel): com.sentinel.ai.ui.components.RiskState = when (level) {
    RiskLevel.GREEN -> com.sentinel.ai.ui.components.RiskState.Safe
    RiskLevel.YELLOW -> com.sentinel.ai.ui.components.RiskState.Suspicious
    RiskLevel.RED -> com.sentinel.ai.ui.components.RiskState.Dangerous
    RiskLevel.CRITICAL -> com.sentinel.ai.ui.components.RiskState.Dangerous
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun AboutScreen(
    appVersion: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = SentinelSpacing.ScreenHorizontal,
                vertical = SentinelSpacing.ScreenVertical
            ),
        verticalArrangement = Arrangement.spacedBy(SentinelSpacing.BetweenSections)
    ) {
        Text(
            text = "Hackathon project built to help users spot scams early and respond with confidence.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SentinelCard {
            Column(modifier = Modifier.fillMaxWidth()) {
                ThreatLevelChip(state = riskState(RiskLevel.GREEN))
                Spacer(modifier = Modifier.height(SentinelSpacing.MD))
                Text(
                    text = "Mission statement",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(SentinelSpacing.XS))
                Text(
                    text = "Protect people from social engineering by turning risky messages into clear, actionable security guidance.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SentinelSectionHeader(
            title = "Project details",
            subtitle = "A compact view of the app identity and source of this prototype"
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SentinelSpacing.MD),
            verticalArrangement = Arrangement.spacedBy(SentinelSpacing.MD)
        ) {
            StatisticCard(
                modifier = Modifier.weight(1f),
                title = "Version",
                value = appVersion,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Code,
                        contentDescription = null,
                        tint = riskColor(RiskLevel.GREEN),
                        modifier = Modifier.size(SentinelSize.IconMedium)
                    )
                },
                subtitle = "UI build identifier for this release"
            )
            StatisticCard(
                modifier = Modifier.weight(1f),
                title = "Type",
                value = "Hackathon",
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = riskColor(RiskLevel.YELLOW),
                        modifier = Modifier.size(SentinelSize.IconMedium)
                    )
                },
                subtitle = "Prototype focused on cybersecurity messaging"
            )
            StatisticCard(
                modifier = Modifier.weight(1f),
                title = "Focus",
                value = "Scam detection",
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = riskColor(RiskLevel.RED),
                        modifier = Modifier.size(SentinelSize.IconMedium)
                    )
                },
                subtitle = "Threat triage, guidance, and user safety"
            )
        }

        SentinelSectionHeader(
            title = "Credits",
            subtitle = "Built with the frozen backend and a Compose-first UI layer"
        )
        SentinelCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(SentinelSpacing.SM)
            ) {
                Text(
                    text = "Android UI and product design",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Sentinel engineering workflow, backend integration, and threat scoring pipeline",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Material 3 and Jetpack Compose",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
