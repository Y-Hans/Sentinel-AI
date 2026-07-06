package com.sentinel.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.ui.theme.SentinelCritical
import com.sentinel.ai.ui.theme.SentinelGreen
import com.sentinel.ai.ui.theme.SentinelRed
import com.sentinel.ai.ui.theme.SentinelYellow

@Composable
fun RiskBadge(
    riskLevel: RiskLevel,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when (riskLevel) {
        RiskLevel.GREEN -> SentinelGreen
        RiskLevel.YELLOW -> SentinelYellow
        RiskLevel.RED -> SentinelRed
        RiskLevel.CRITICAL -> SentinelCritical
    }

    Text(
        text = riskLevel.displayLabel(),
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(backgroundColor.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        style = MaterialTheme.typography.labelLarge,
        color = backgroundColor
    )
}


