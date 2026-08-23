package com.sentinel.ai.warning

import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.core.warning.toWarningUiModel as coreToWarningUiModel

typealias WarningSeverity = com.sentinel.ai.core.warning.WarningSeverity
typealias WarningUiModel = com.sentinel.ai.core.warning.WarningUiModel

fun ScanResult.toWarningUiModel(): WarningUiModel = this.coreToWarningUiModel()
