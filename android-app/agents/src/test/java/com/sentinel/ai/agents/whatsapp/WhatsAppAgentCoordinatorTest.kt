package com.sentinel.ai.agents.whatsapp

import com.sentinel.ai.agents.registry.SupportedAppRegistry
import com.sentinel.ai.core.data.local.ThreatDao
import com.sentinel.ai.core.data.local.ThreatRecordEntity
import com.sentinel.ai.core.event.ThreatEvent
import com.sentinel.ai.core.event.ThreatEventBus
import com.sentinel.ai.core.event.ThreatJournal
import com.sentinel.ai.core.model.ProtectionDecision
import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.core.warning.WarningNotificationDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

class WhatsAppAgentCoordinatorTest {

    private lateinit var fakeDao: FakeThreatDao
    private lateinit var fakeDispatcher: FakeWarningDispatcher

    @Before
    fun setUp() {
        fakeDao = FakeThreatDao()
        fakeDispatcher = FakeWarningDispatcher()
        ThreatJournal.resetForTesting()
        ThreatJournal.initialize(fakeDao, preload = false)
    }

    @After
    fun tearDown() {
        ThreatJournal.resetForTesting()
    }

    @Test
    fun `publishes WhatsApp threat event for valid notification`() = runTest {
        val bus = ThreatEventBus()
        val coordinator = WhatsAppAgentCoordinator(
            parser = WhatsAppNotificationParser(SupportedAppRegistry()),
            builder = WhatsAppEventBuilder(),
            threatEventBus = bus,
            threatJournal = ThreatJournal,
            warningDispatcher = fakeDispatcher
        )
        val emitted = async(start = CoroutineStart.UNDISPATCHED) { bus.events.first() }

        coordinator.onWhatsAppNotification(
            WhatsAppNotificationSnapshot(
                packageName = "com.whatsapp",
                notificationKey = "wa-key",
                sender = "John Doe",
                message = "Urgent payment request",
                timestampMs = 1_719_218_400_000L
            )
        )

        val event = emitted.await()
        assertTrue(event is ThreatEvent.WhatsAppThreatDetected)
        assertEquals("COMPLETED", coordinator.lastStatus.value)
        assertEquals("com.whatsapp", (event as ThreatEvent.WhatsAppThreatDetected).scanResult.source)
        assertEquals(RiskLevel.YELLOW, event.scanResult.riskLevel)
    }

    @Test
    fun `test1 Valid notification directly reaches ThreatJournal and persists to Room`() = runTest {
        val bus = ThreatEventBus()
        val coordinator = WhatsAppAgentCoordinator(
            parser = WhatsAppNotificationParser(SupportedAppRegistry()),
            builder = WhatsAppEventBuilder(),
            threatEventBus = bus,
            threatJournal = ThreatJournal,
            warningDispatcher = fakeDispatcher
        )

        coordinator.onWhatsAppNotification(
            WhatsAppNotificationSnapshot(
                packageName = "com.whatsapp",
                notificationKey = "wa-direct-1",
                sender = "John Doe",
                message = "Urgent transfer required immediately",
                timestampMs = 1_719_218_400_000L
            )
        )

        assertEquals("COMPLETED", coordinator.lastStatus.value)
        assertEquals(1, fakeDao.persistedRecords.size)
        assertEquals(1, ThreatJournal.scanResults.value.size)
        val persisted = ThreatJournal.scanResults.value.first()
        assertEquals("com.whatsapp", persisted.source)
        assertEquals("John Doe", persisted.senderDisplayName)
    }

    @Test
    fun `test2 and test3 Persistence occurs without ThreatEventBus subscribers or subscriber service`() = runTest {
        // Zero subscribers on ThreatEventBus
        val bus = ThreatEventBus()
        val coordinator = WhatsAppAgentCoordinator(
            parser = WhatsAppNotificationParser(SupportedAppRegistry()),
            builder = WhatsAppEventBuilder(),
            threatEventBus = bus,
            threatJournal = ThreatJournal,
            warningDispatcher = fakeDispatcher
        )

        assertEquals(0, bus.events.replayCache.size)

        coordinator.onWhatsAppNotification(
            WhatsAppNotificationSnapshot(
                packageName = "org.telegram.messenger",
                notificationKey = "tg-key-1",
                sender = "Scammer",
                message = "Your bank account is suspended immediately",
                timestampMs = 1_719_218_400_000L
            )
        )

        assertEquals("COMPLETED", coordinator.lastStatus.value)
        assertEquals(1, fakeDao.persistedRecords.size)
        assertEquals(1, ThreatJournal.scanResults.value.size)
    }

    @Test
    fun `test4 ThreatJournal suspending persistence is actually awaited by coordinator`() = runTest {
        val daoGate = CompletableDeferred<Unit>()
        val executionOrder = mutableListOf<String>()

        val blockingDao = object : ThreatDao {
            val records = mutableMapOf<String, ThreatRecordEntity>()
            override suspend fun getAllThreatRecords(): List<ThreatRecordEntity> = records.values.toList()
            override suspend fun upsertThreatRecord(record: ThreatRecordEntity) {
                executionOrder += "dao_upsert_start"
                daoGate.await()
                records["${record.id}|${record.recordType}"] = record
                executionOrder += "dao_upsert_end"
            }
        }

        ThreatJournal.resetForTesting()
        ThreatJournal.initialize(blockingDao, preload = false)

        val coordinator = WhatsAppAgentCoordinator(
            parser = WhatsAppNotificationParser(SupportedAppRegistry()),
            builder = WhatsAppEventBuilder(),
            threatEventBus = ThreatEventBus(),
            threatJournal = ThreatJournal,
            warningDispatcher = fakeDispatcher
        )

        val job = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.onWhatsAppNotification(
                WhatsAppNotificationSnapshot(
                    packageName = "com.whatsapp",
                    notificationKey = "order-key",
                    sender = "John",
                    message = "Urgent: payment deadline",
                    timestampMs = 1_719_218_400_000L
                )
            )
            executionOrder += "coordinator_completed"
        }

        // DAO upsert is in flight
        assertEquals("CAPTURED", coordinator.lastStatus.value)

        // Release the gate
        daoGate.complete(Unit)
        job.await()

        assertEquals("COMPLETED", coordinator.lastStatus.value)
        assertEquals(
            listOf("dao_upsert_start", "dao_upsert_end", "coordinator_completed"),
            executionOrder
        )
    }

    @Test
    fun `test5 and test6 Elevated WARN and BLOCK results trigger WarningNotificationDispatcher`() = runTest {
        val coordinator = WhatsAppAgentCoordinator(
            parser = WhatsAppNotificationParser(SupportedAppRegistry()),
            builder = WhatsAppEventBuilder(),
            threatEventBus = ThreatEventBus(),
            threatJournal = ThreatJournal,
            warningDispatcher = fakeDispatcher
        )

        // 1. Elevated BLOCK (Critical)
        coordinator.onWhatsAppNotification(
            WhatsAppNotificationSnapshot(
                packageName = "com.whatsapp",
                notificationKey = "block-key",
                sender = "Attacker",
                message = "Urgent: verify your account OTP immediately for bank login",
                timestampMs = 1_719_218_400_000L
            )
        )

        assertEquals(1, fakeDispatcher.dispatchedWarnings.size)
        val blockWarning = fakeDispatcher.dispatchedWarnings.first()
        assertTrue(blockWarning.second) // highPriority = true
        assertEquals(ProtectionDecision.BLOCK, blockWarning.first.decision)

        // 2. Elevated WARN (Medium / High)
        fakeDispatcher.dispatchedWarnings.clear()
        coordinator.onWhatsAppNotification(
            WhatsAppNotificationSnapshot(
                packageName = "com.whatsapp",
                notificationKey = "warn-key",
                sender = "Sender",
                message = "Urgent payment required",
                timestampMs = 1_719_218_500_000L
            )
        )

        assertEquals(1, fakeDispatcher.dispatchedWarnings.size)
        val warnWarning = fakeDispatcher.dispatchedWarnings.first()
        assertEquals(ProtectionDecision.WARN, warnWarning.first.decision)
    }

    @Test
    fun `test6 ALLOW benign results do not trigger warnings`() = runTest {
        val coordinator = WhatsAppAgentCoordinator(
            parser = WhatsAppNotificationParser(SupportedAppRegistry()),
            builder = WhatsAppEventBuilder(),
            threatEventBus = ThreatEventBus(),
            threatJournal = ThreatJournal,
            warningDispatcher = fakeDispatcher
        )

        // Benign notification from known contact
        coordinator.onWhatsAppNotification(
            snapshot = WhatsAppNotificationSnapshot(
                packageName = "com.whatsapp",
                notificationKey = "safe-key",
                sender = "Alice",
                message = "Hey, are you free for lunch?",
                timestampMs = 1_719_218_600_000L
            ),
            isKnownContact = true
        )

        assertEquals("COMPLETED", coordinator.lastStatus.value)
        assertEquals(0, fakeDispatcher.dispatchedWarnings.size)
    }

    @Test
    fun `test7 Persistence failure propagates and does not mark completed or dispatch warning`() = runTest {
        fakeDao.shouldFailUpsert = true

        val coordinator = WhatsAppAgentCoordinator(
            parser = WhatsAppNotificationParser(SupportedAppRegistry()),
            builder = WhatsAppEventBuilder(),
            threatEventBus = ThreatEventBus(),
            threatJournal = ThreatJournal,
            warningDispatcher = fakeDispatcher
        )

        try {
            coordinator.onWhatsAppNotification(
                WhatsAppNotificationSnapshot(
                    packageName = "com.whatsapp",
                    notificationKey = "fail-key",
                    sender = "Attacker",
                    message = "Urgent transfer",
                    timestampMs = 1_719_218_400_000L
                )
            )
            fail("Expected exception on database persistence failure")
        } catch (e: Exception) {
            assertTrue(e is IOException)
        }

        // Warning was NOT dispatched and status was NOT set to COMPLETED
        assertEquals(0, fakeDispatcher.dispatchedWarnings.size)
        assertEquals("CAPTURED", coordinator.lastStatus.value)
    }

    @Test
    fun `ignores unsupported packages`() = runTest {
        val bus = ThreatEventBus()
        val coordinator = WhatsAppAgentCoordinator(
            parser = WhatsAppNotificationParser(SupportedAppRegistry()),
            builder = WhatsAppEventBuilder(),
            threatEventBus = bus,
            threatJournal = ThreatJournal,
            warningDispatcher = fakeDispatcher
        )

        coordinator.onWhatsAppNotification(
            WhatsAppNotificationSnapshot(
                packageName = "com.example.fake",
                notificationKey = "other-key",
                sender = "John Doe",
                message = "Hello",
                timestampMs = 1_719_218_400_000L
            )
        )

        assertEquals("IGNORED", coordinator.lastStatus.value)
        assertTrue(bus.events.replayCache.isEmpty())
    }

    @Test
    fun `suppresses duplicate reposts for the same notification`() = runTest {
        val bus = ThreatEventBus()
        val coordinator = WhatsAppAgentCoordinator(
            parser = WhatsAppNotificationParser(SupportedAppRegistry()),
            builder = WhatsAppEventBuilder(),
            threatEventBus = bus,
            threatJournal = ThreatJournal,
            warningDispatcher = fakeDispatcher
        )
        val events = mutableListOf<ThreatEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            bus.events.collect { events += it }
        }
        val snapshot = WhatsAppNotificationSnapshot(
            packageName = "com.whatsapp",
            notificationKey = "wa-key",
            sender = "John Doe",
            message = "Urgent payment request",
            timestampMs = 1_719_218_400_000L
        )

        coordinator.onWhatsAppNotification(snapshot)
        coordinator.onWhatsAppNotification(snapshot)
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals(1, fakeDao.persistedRecords.size)
        assertEquals("IGNORED", coordinator.lastStatus.value)
        collector.cancel()
    }

    @Test
    fun `always publishes repeated block notifications`() = runTest {
        val bus = ThreatEventBus()
        val coordinator = WhatsAppAgentCoordinator(
            parser = WhatsAppNotificationParser(SupportedAppRegistry()),
            builder = WhatsAppEventBuilder(),
            threatEventBus = bus,
            threatJournal = ThreatJournal,
            warningDispatcher = fakeDispatcher
        )
        val events = mutableListOf<ThreatEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            bus.events.collect { events += it }
        }
        val snapshot = WhatsAppNotificationSnapshot(
            packageName = "com.whatsapp",
            notificationKey = "critical-key",
            sender = "Unknown Sender",
            message = "Urgent: verify your account OTP immediately for bank login",
            timestampMs = 1_719_218_400_000L
        )

        coordinator.onWhatsAppNotification(snapshot)
        coordinator.onWhatsAppNotification(snapshot)
        advanceUntilIdle()

        assertEquals(2, events.size)
        events.forEach { event ->
            assertEquals(
                RiskLevel.CRITICAL,
                (event as ThreatEvent.WhatsAppThreatDetected).scanResult.riskLevel
            )
        }
        assertEquals("COMPLETED", coordinator.lastStatus.value)
        collector.cancel()
    }

    private class FakeThreatDao : ThreatDao {
        val persistedRecords = mutableMapOf<String, ThreatRecordEntity>()
        var shouldFailUpsert = false

        override suspend fun getAllThreatRecords(): List<ThreatRecordEntity> {
            return persistedRecords.values.sortedByDescending { it.timestamp }
        }

        override suspend fun upsertThreatRecord(record: ThreatRecordEntity) {
            if (shouldFailUpsert) {
                throw IOException("Simulated database write error")
            }
            persistedRecords["${record.id}|${record.recordType}"] = record
        }
    }

    private class FakeWarningDispatcher : WarningNotificationDispatcher {
        val dispatchedWarnings = mutableListOf<Pair<ScanResult, Boolean>>()

        override fun showWarning(result: ScanResult, highPriority: Boolean) {
            dispatchedWarnings += result to highPriority
        }
    }
}
