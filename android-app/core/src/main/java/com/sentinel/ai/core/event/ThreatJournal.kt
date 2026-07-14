package com.sentinel.ai.core.event

import android.content.Context
import com.sentinel.ai.core.data.local.SentinelDatabase
import com.sentinel.ai.core.data.local.ThreatDao
import com.sentinel.ai.core.data.local.ThreatRecordEntity
import com.sentinel.ai.core.data.local.toEntity
import com.sentinel.ai.core.data.local.toScanResult
import com.sentinel.ai.core.data.local.toThreat
import com.sentinel.ai.core.model.Alert
import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.core.model.Threat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object ThreatJournal {

    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults.asStateFlow()

    private val _threats = MutableStateFlow<List<Threat>>(emptyList())
    val threats: StateFlow<List<Threat>> = _threats.asStateFlow()

    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    val alerts: StateFlow<List<Alert>> = _alerts.asStateFlow()

    @Volatile
    private var initialized = false
    private var threatDao: ThreatDao? = null

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            threatDao = SentinelDatabase.getInstance(context).threatDao()
            restoreState()
            initialized = true
        }
    }

    fun record(event: ThreatEvent) {
        when (event) {
            is ThreatEvent.SmsThreatDetected -> appendScanResult(event.scanResult)
            is ThreatEvent.CallThreatDetected -> appendScanResult(event.scanResult)
            is ThreatEvent.LinkThreatDetected -> appendScanResult(event.scanResult)
            is ThreatEvent.FileThreatDetected -> appendScanResult(event.scanResult)
            is ThreatEvent.WhatsAppThreatDetected -> appendScanResult(event.scanResult)
            is ThreatEvent.CriticalThreatAlert -> appendThreat(event.threat)
            ThreatEvent.GuardActivated,
            ThreatEvent.GuardDeactivated -> Unit
        }
    }

    fun threatFor(threatId: String): Threat? {
        threats.value.firstOrNull { it.id == threatId }?.let { return it }
        return scanResults.value.firstOrNull { it.id == threatId }?.toThreat()
    }

    fun observeThreat(threatId: String): Flow<Threat?> {
        return combine(threats, scanResults) { threats, scanResults ->
            threats.firstOrNull { it.id == threatId }
                ?: scanResults.firstOrNull { it.id == threatId }?.toThreat()
        }.distinctUntilChanged()
    }

    private fun appendScanResult(scanResult: ScanResult) {
        _scanResults.update { current ->
            current.updateById(scanResult.id, scanResult)
        }
        rebuildAlerts()
        persistScanResult(scanResult)
    }

    private fun appendThreat(threat: Threat) {
        _threats.update { current ->
            current.updateById(threat.id, threat)
        }
        rebuildAlerts()
        persistThreat(threat)
    }

    private fun persistScanResult(scanResult: ScanResult) {
        val dao = threatDao ?: return
        persistenceScope.launch {
            runCatching {
                dao.upsertThreatRecord(scanResult.toEntity())
            }
        }
    }

    private fun persistThreat(threat: Threat) {
        val dao = threatDao ?: return
        persistenceScope.launch {
            runCatching {
                dao.upsertThreatRecord(threat.toEntity())
            }
        }
    }

    private fun rebuildAlerts() {
        val derivedAlerts = buildList {
            addAll(_scanResults.value.map { it.toAlert() })
            addAll(_threats.value.map { it.toAlert() })
        }.distinctBy { it.id }
            .sortedByDescending { it.timestamp }

        _alerts.value = derivedAlerts
    }

    private fun restoreState() {
        val dao = threatDao ?: return
        val persistedRecords = runBlocking(Dispatchers.IO) {
            runCatching {
                dao.getAllThreatRecords()
            }.getOrDefault(emptyList())
        }

        _scanResults.value = persistedRecords
            .asSequence()
            .filter { it.recordType == ThreatRecordEntity.TYPE_SCAN_RESULT }
            .map { it.toScanResult() }
            .sortedByDescending { it.timestamp }
            .toList()

        _threats.value = persistedRecords
            .asSequence()
            .filter { it.recordType == ThreatRecordEntity.TYPE_THREAT }
            .map { it.toThreat() }
            .sortedByDescending { it.timestamp }
            .toList()

        rebuildAlerts()
    }

    private fun List<ScanResult>.updateById(
        id: String,
        item: ScanResult
    ): List<ScanResult> {
        return toMutableList().apply {
            val index = indexOfFirst { it.id == id }
            if (index >= 0) {
                set(index, item)
            } else {
                add(item)
            }
        }.sortedByDescending { it.timestamp }
    }

    private fun List<Threat>.updateById(
        id: String,
        item: Threat
    ): List<Threat> {
        return toMutableList().apply {
            val index = indexOfFirst { it.id == id }
            if (index >= 0) {
                set(index, item)
            } else {
                add(item)
            }
        }.sortedByDescending { it.timestamp }
    }

    private fun ScanResult.toAlert(): Alert {
        return Alert(
            id = id,
            threatId = id,
            title = source,
            senderDisplayName = senderDisplayName,
            senderIdentifier = senderIdentifier,
            summary = explanation,
            riskLevel = riskLevel,
            timestamp = timestamp
        )
    }

    private fun Threat.toAlert(): Alert {
        return Alert(
            id = id,
            threatId = id,
            title = source,
            senderDisplayName = senderDisplayName,
            senderIdentifier = senderIdentifier,
            summary = explanation,
            riskLevel = riskLevel,
            timestamp = timestamp
        )
    }

    private fun ScanResult.toThreat(): Threat {
        return Threat(
            id = id,
            source = source,
            senderDisplayName = senderDisplayName,
            senderIdentifier = senderIdentifier,
            content = target ?: explanation,
            riskLevel = riskLevel,
            riskScore = riskScore,
            explanation = explanation,
            recommendation = when (riskLevel) {
                RiskLevel.GREEN -> "No immediate action is required."
                RiskLevel.YELLOW -> "Verify the sender through a trusted channel before responding."
                RiskLevel.RED -> "Do not engage with the message and block the sender if needed."
                RiskLevel.CRITICAL -> "Block the sender, preserve evidence, and report the threat immediately."
            },
            timestamp = timestamp
        )
    }
}
