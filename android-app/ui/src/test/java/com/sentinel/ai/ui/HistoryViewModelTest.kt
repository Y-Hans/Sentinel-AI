package com.sentinel.ai.ui

import com.sentinel.ai.core.data.local.ThreatDao
import com.sentinel.ai.core.data.local.ThreatRecordEntity
import com.sentinel.ai.ui.screens.history.HistoryUiAction
import com.sentinel.ai.ui.screens.history.HistoryViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createFakeDao(records: MutableList<ThreatRecordEntity>, delayDeferred: CompletableDeferred<Unit>? = null): ThreatDao {
        return object : ThreatDao {
            override suspend fun getRecentThreatRecords(limit: Int): List<ThreatRecordEntity> {
                return records.take(limit)
            }
            override suspend fun getThreatRecordsBefore(recordType: String, cursorTimestamp: Long, cursorId: String, limit: Int): List<ThreatRecordEntity> {
                delayDeferred?.await()
                return records
                    .filter {
                        it.recordType == recordType &&
                            (it.timestamp < cursorTimestamp || (it.timestamp == cursorTimestamp && it.id < cursorId))
                    }
                    .sortedWith(compareByDescending<ThreatRecordEntity> { it.timestamp }.thenByDescending { it.id })
                    .take(limit)
            }
            override suspend fun upsertThreatRecord(record: ThreatRecordEntity) {
                records.add(record)
                records.sortWith(compareByDescending<ThreatRecordEntity> { it.timestamp }.thenByDescending { it.id })
            }
        }
    }

    private fun generateRecords(count: Int, fixedTimestamp: Long? = null): MutableList<ThreatRecordEntity> {
        return (1..count).map { i ->
            val id = "scan-%04d".format(i)
            ThreatRecordEntity(
                id = id,
                recordType = ThreatRecordEntity.TYPE_SCAN_RESULT,
                source = "com.whatsapp",
                senderDisplayName = "Sender $i",
                senderIdentifier = "+1000000$i",
                content = null,
                riskLevel = "GREEN",
                riskScore = 10f,
                explanation = "OK",
                recommendation = null,
                timestamp = fixedTimestamp ?: i.toLong()
            )
        }.sortedWith(compareByDescending<ThreatRecordEntity> { it.timestamp }.thenByDescending { it.id }).toMutableList()
    }

    @Test
    fun `initial page + second page`() = runTest {
        val records = generateRecords(200)
        val fakeDao = createFakeDao(records)
        val viewModel = HistoryViewModel(fakeDao)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(100, viewModel.uiState.value.history.size)
        assertEquals("scan-0200", viewModel.uiState.value.history.first().id)
        assertEquals("scan-0101", viewModel.uiState.value.history.last().id)

        viewModel.onAction(HistoryUiAction.LoadMore)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(200, viewModel.uiState.value.history.size)
        assertEquals("scan-0100", viewModel.uiState.value.history[100].id)
        assertEquals("scan-0001", viewModel.uiState.value.history.last().id)
    }

    @Test
    fun `fewer than page-size records`() = runTest {
        val records = generateRecords(50)
        val fakeDao = createFakeDao(records)
        val viewModel = HistoryViewModel(fakeDao)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(50, viewModel.uiState.value.history.size)

        viewModel.onAction(HistoryUiAction.LoadMore)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(50, viewModel.uiState.value.history.size)
    }

    @Test
    fun `exactly page-size records`() = runTest {
        val records = generateRecords(100)
        val fakeDao = createFakeDao(records)
        val viewModel = HistoryViewModel(fakeDao)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(100, viewModel.uiState.value.history.size)

        viewModel.onAction(HistoryUiAction.LoadMore)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(100, viewModel.uiState.value.history.size)
    }

    @Test
    fun `new scan inserted between page loads`() = runTest {
        val records = generateRecords(150)
        val fakeDao = createFakeDao(records)
        val viewModel = HistoryViewModel(fakeDao)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(100, viewModel.uiState.value.history.size) // Has scan-0150..scan-0051

        // Insert new scan at timestamp 200 (between loads)
        fakeDao.upsertThreatRecord(
            ThreatRecordEntity("scan-new", ThreatRecordEntity.TYPE_SCAN_RESULT, "src", null, null, null, "GREEN", 0f, "ok", null, 200L)
        )

        viewModel.onAction(HistoryUiAction.LoadMore)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(150, viewModel.uiState.value.history.size)
        assertEquals("scan-0001", viewModel.uiState.value.history.last().id)

        val ids = viewModel.uiState.value.history.map { it.id }.toSet()
        assertEquals(150, ids.size)
    }

    @Test
    fun `multiple LoadMore calls while a load is in progress`() = runTest {
        val records = generateRecords(200)
        val delayDeferred = CompletableDeferred<Unit>()
        val fakeDao = object : ThreatDao {
            val callCount = AtomicInteger(0)
            override suspend fun getRecentThreatRecords(limit: Int): List<ThreatRecordEntity> = emptyList()
            override suspend fun getThreatRecordsBefore(recordType: String, cursorTimestamp: Long, cursorId: String, limit: Int): List<ThreatRecordEntity> {
                callCount.incrementAndGet()
                delayDeferred.await()
                return records
                    .filter {
                        it.recordType == recordType &&
                            (it.timestamp < cursorTimestamp || (it.timestamp == cursorTimestamp && it.id < cursorId))
                    }
                    .sortedWith(compareByDescending<ThreatRecordEntity> { it.timestamp }.thenByDescending { it.id })
                    .take(limit)
            }
            override suspend fun upsertThreatRecord(record: ThreatRecordEntity) {}
        }

        val viewModel = HistoryViewModel(fakeDao)

        // Init triggers loadMore once.
        // Multiple LoadMore calls while load is in progress should be ignored.
        viewModel.onAction(HistoryUiAction.LoadMore)
        viewModel.onAction(HistoryUiAction.LoadMore)
        viewModel.onAction(HistoryUiAction.LoadMore)

        delayDeferred.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeDao.callCount.get())
        assertEquals(100, viewModel.uiState.value.history.size)
    }

    @Test
    fun `no duplicate IDs`() = runTest {
        val records = generateRecords(150)
        // Add a duplicate ID manually
        records.add(records[0].copy(timestamp = 0L))
        val fakeDao = createFakeDao(records)
        val viewModel = HistoryViewModel(fakeDao)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(HistoryUiAction.LoadMore)
        testDispatcher.scheduler.advanceUntilIdle()

        val ids = viewModel.uiState.value.history.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `multiple records share the same timestamp across a page boundary`() = runTest {
        // 150 records all sharing the exact same timestamp 1000L
        val records = generateRecords(150, fixedTimestamp = 1000L)
        val fakeDao = createFakeDao(records)
        val viewModel = HistoryViewModel(fakeDao)
        testDispatcher.scheduler.advanceUntilIdle()

        // Page 1: 100 items (scan-0150 down to scan-0051)
        assertEquals(100, viewModel.uiState.value.history.size)
        assertEquals("scan-0150", viewModel.uiState.value.history.first().id)
        assertEquals("scan-0051", viewModel.uiState.value.history.last().id)

        // Load Page 2: should seamlessly fetch remaining 50 items sharing timestamp 1000L
        viewModel.onAction(HistoryUiAction.LoadMore)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(150, viewModel.uiState.value.history.size)
        assertEquals("scan-0050", viewModel.uiState.value.history[100].id)
        assertEquals("scan-0001", viewModel.uiState.value.history.last().id)

        val uniqueIds = viewModel.uiState.value.history.map { it.id }.toSet()
        assertEquals(150, uniqueIds.size)
    }

    @Test
    fun `new scan inserted with the same timestamp as the current cursor`() = runTest {
        // 150 records all sharing timestamp 1000L
        val records = generateRecords(150, fixedTimestamp = 1000L)
        val fakeDao = createFakeDao(records)
        val viewModel = HistoryViewModel(fakeDao)
        testDispatcher.scheduler.advanceUntilIdle()

        // Page 1 ends at cursor (timestamp = 1000L, id = "scan-0051")
        assertEquals(100, viewModel.uiState.value.history.size)
        assertEquals("scan-0051", viewModel.uiState.value.history.last().id)

        // 1. Insert a new scan with same timestamp 1000L and an ID > cursorId (belongs to page 1)
        fakeDao.upsertThreatRecord(
            ThreatRecordEntity("scan-0099-new", ThreatRecordEntity.TYPE_SCAN_RESULT, "src", null, null, null, "GREEN", 0f, "ok", null, 1000L)
        )

        // 2. Insert a new scan with same timestamp 1000L and an ID < cursorId (belongs to page 2)
        fakeDao.upsertThreatRecord(
            ThreatRecordEntity("scan-0000-new", ThreatRecordEntity.TYPE_SCAN_RESULT, "src", null, null, null, "GREEN", 0f, "ok", null, 1000L)
        )

        // Load Page 2
        viewModel.onAction(HistoryUiAction.LoadMore)
        testDispatcher.scheduler.advanceUntilIdle()

        val history = viewModel.uiState.value.history
        // Page 2 fetches items where (timestamp == 1000L AND id < "scan-0051")
        // This includes "scan-0050".."scan-0001" (50 items) + "scan-0000-new" (1 item) = 51 items
        assertEquals(151, history.size)
        assertTrue(history.any { it.id == "scan-0000-new" })
        // scan-0099-new has id > "scan-0051", so it was not loaded into page 2
        assertFalse(history.any { it.id == "scan-0099-new" })

        val uniqueIds = history.map { it.id }.toSet()
        assertEquals(151, uniqueIds.size)
    }
}

