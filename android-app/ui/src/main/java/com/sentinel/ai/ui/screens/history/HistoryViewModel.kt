package com.sentinel.ai.ui.screens.history

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.core.data.local.toScanResult
import com.sentinel.ai.core.event.ThreatJournal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val threatDao: com.sentinel.ai.core.data.local.ThreatDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val pageSize = 100
    private var isEndReached = false
    private var isLoading = false
    private var cursorTimestamp: Long = Long.MAX_VALUE
    private var cursorId: String = ""

    init {
        loadMore()
        observeJournalChanges()
    }

    fun onAction(action: HistoryUiAction) {
        when (action) {
            HistoryUiAction.Refresh -> refresh()
            HistoryUiAction.LoadMore -> loadMore()
        }
    }

    private fun refresh() {
        cursorTimestamp = Long.MAX_VALUE
        cursorId = ""
        isEndReached = false
        isLoading = false
        _uiState.update { it.copy(history = emptyList()) }
        loadMore()
    }

    private fun loadMore() {
        if (isEndReached || isLoading) return
        isLoading = true
        viewModelScope.launch {
            try {
                val newEntities = threatDao.getThreatRecordsBefore(
                    recordType = com.sentinel.ai.core.data.local.ThreatRecordEntity.TYPE_SCAN_RESULT,
                    cursorTimestamp = cursorTimestamp,
                    cursorId = cursorId,
                    limit = pageSize
                )

                if (newEntities.isEmpty()) {
                    isEndReached = true
                } else {
                    val lastRecord = newEntities.last()
                    cursorTimestamp = lastRecord.timestamp
                    cursorId = lastRecord.id
                    if (newEntities.size < pageSize) {
                        isEndReached = true
                    }
                    val newScans = newEntities.map { it.toScanResult() }

                    _uiState.update { current ->
                        val existingIds = current.history.map { it.id }.toSet()
                        val distinctNewScans = newScans.filter { it.id !in existingIds }
                        current.copy(history = current.history + distinctNewScans)
                    }
                }
            } finally {
                isLoading = false
            }
        }
    }

    private fun observeJournalChanges() {
        viewModelScope.launch {
            ThreatJournal.scanResults.collectLatest { journalHistory ->
                _uiState.update { current ->
                    val currentIds = current.history.map { it.id }.toSet()
                    val newFromJournal = journalHistory.filter { it.id !in currentIds }

                    if (newFromJournal.isNotEmpty()) {
                        current.copy(history = newFromJournal + current.history)
                    } else {
                        val updatedList = current.history.map { existing ->
                            journalHistory.find { it.id == existing.id } ?: existing
                        }
                        current.copy(history = updatedList)
                    }
                }
            }
        }
    }
}
