package com.sentinel.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.ui.theme.SentinelCritical
import com.sentinel.ai.ui.theme.SentinelCyan
import com.sentinel.ai.ui.theme.SentinelGreen
import com.sentinel.ai.ui.theme.SentinelOutline
import com.sentinel.ai.ui.theme.SentinelRed
import com.sentinel.ai.ui.theme.SentinelSurface
import com.sentinel.ai.ui.theme.SentinelSurfaceVariant
import com.sentinel.ai.ui.theme.SentinelYellow

@Composable
fun SentinelCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val cardModifier = if (onClick != null) {
        modifier.fillMaxWidth().clickable(onClick = onClick)
    } else {
        modifier.fillMaxWidth()
    }

    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = SentinelSurface.copy(alpha = 0.96f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, SentinelOutline.copy(alpha = 0.65f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            content()
        }
    }
}

@Composable
fun SentinelSectionHeader(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = SentinelCyan,
                modifier = Modifier.clickable(onClick = onAction)
            )
        }
    }
}

@Composable
fun SentinelMetricCard(
    label: String,
    value: String,
    accent: Color,
    supportingText: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
        color = SentinelSurfaceVariant.copy(alpha = 0.8f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SentinelPill(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text = label,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent.copy(alpha = 0.15f))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = accent,
        style = MaterialTheme.typography.labelLarge
    )
}

@Composable
fun SentinelIndicatorDot(
    color: Color,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(10.dp)
            .clip(RoundedCornerShape(50.dp))
            .background(color)
    )
}

internal fun riskColor(riskLevel: RiskLevel): Color {
    return when (riskLevel) {
        RiskLevel.GREEN -> SentinelGreen
        RiskLevel.YELLOW -> SentinelYellow
        RiskLevel.RED -> SentinelRed
        RiskLevel.CRITICAL -> SentinelCritical
    }
}

internal fun RiskLevel.displayLabel(): String {
    return when (this) {
        RiskLevel.GREEN -> "Low"
        RiskLevel.YELLOW -> "Moderate"
        RiskLevel.RED -> "High"
        RiskLevel.CRITICAL -> "Critical"
    }
}
