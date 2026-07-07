package com.sentinel.ai.protection.intent.reputation

import com.sentinel.ai.core.coroutines.DispatcherProvider
import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.core.model.ScanResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ReputationManagerImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockDispatcherProvider = object : DispatcherProvider() {
        override val io: CoroutineDispatcher = testDispatcher
        override val main: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private val combiner = EvidenceCombiner()
    private val baseResult = ScanResult(
        id = "test",
        source = "test",
        riskLevel = RiskLevel.GREEN,
        riskScore = 0f,
        explanation = "Clean.",
        timestamp = System.currentTimeMillis()
    )

    @Test
    fun returnsHeuristicResultIfTargetIsNull() = runTest(testDispatcher) {
        val manager = ReputationManagerImpl(
            providers = setOf(object : ReputationProvider {
                override val providerName = "FailProvider"
                override suspend fun evaluate(target: ReputationTarget) = error("Should not be called")
            }),
            combiner = combiner,
            dispatcherProvider = mockDispatcherProvider,
            config = ReputationConfig("", "", 5000L)
        )

        val result = manager.enrich(baseResult, null)
        assertEquals(baseResult, result)
    }

    @Test
    fun returnsHeuristicResultIfNoProvidersRegistered() = runTest(testDispatcher) {
        val manager = ReputationManagerImpl(
            providers = emptySet(),
            combiner = combiner,
            dispatcherProvider = mockDispatcherProvider,
            config = ReputationConfig("", "", 5000L)
        )

        val result = manager.enrich(baseResult, ReputationTarget.Url("https://malicious.com"))
        assertEquals(baseResult, result)
    }

    @Test
    fun queriesActiveProvidersAndCombinesResults() = runTest(testDispatcher) {
        val provider = object : ReputationProvider {
            override val providerName = "ProviderA"
            override suspend fun evaluate(target: ReputationTarget): ReputationResult {
                return ReputationResult("ProviderA", 0.9f, ReputationVerdict.MALICIOUS, "Bad", 0L)
            }
        }

        val manager = ReputationManagerImpl(
            providers = setOf(provider),
            combiner = combiner,
            dispatcherProvider = mockDispatcherProvider,
            config = ReputationConfig("", "", 5000L)
        )

        val result = manager.enrich(baseResult, ReputationTarget.Url("https://malicious.com"))
        assertTrue(result.riskScore > 0f)
        assertTrue(result.explanation.contains("ProviderA=malicious"))
    }

    @Test
    fun ignoresProviderExceptionAndGracefullyFallsBack() = runTest(testDispatcher) {
        val buggyProvider = object : ReputationProvider {
            override val providerName = "BuggyProvider"
            override suspend fun evaluate(target: ReputationTarget): ReputationResult {
                throw IOException("Network error")
            }
        }
        val goodProvider = object : ReputationProvider {
            override val providerName = "GoodProvider"
            override suspend fun evaluate(target: ReputationTarget): ReputationResult {
                return ReputationResult("GoodProvider", 0.9f, ReputationVerdict.MALICIOUS, "Bad", 0L)
            }
        }

        val manager = ReputationManagerImpl(
            providers = setOf(buggyProvider, goodProvider),
            combiner = combiner,
            dispatcherProvider = mockDispatcherProvider,
            config = ReputationConfig("", "", 5000L)
        )

        val result = manager.enrich(baseResult, ReputationTarget.Url("https://malicious.com"))
        // The buggy provider fails but the good provider result is still integrated successfully
        assertTrue(result.riskScore > 0f)
        assertTrue(result.explanation.contains("GoodProvider=malicious"))
        // Buggy provider explanation shouldn't be in the combined description
        assertTrue(!result.explanation.contains("BuggyProvider"))
    }

    @Test
    fun respectsTimeoutAndExcludesSlowProviders() = runTest(testDispatcher) {
        val slowProvider = object : ReputationProvider {
            override val providerName = "SlowProvider"
            override suspend fun evaluate(target: ReputationTarget): ReputationResult {
                delay(2000L)
                return ReputationResult("SlowProvider", 0.9f, ReputationVerdict.MALICIOUS, "Bad", 0L)
            }
        }
        val fastProvider = object : ReputationProvider {
            override val providerName = "FastProvider"
            override suspend fun evaluate(target: ReputationTarget): ReputationResult {
                return ReputationResult("FastProvider", 0.8f, ReputationVerdict.SUSPICIOUS, "Suspect", 0L)
            }
        }

        val manager = ReputationManagerImpl(
            providers = setOf(slowProvider, fastProvider),
            combiner = combiner,
            dispatcherProvider = mockDispatcherProvider,
            config = ReputationConfig("", "", 500L) // 500ms timeout
        )

        // Run the task and advance the clock
        val resultDeferred = async {
            manager.enrich(baseResult, ReputationTarget.Url("https://malicious.com"))
        }

        // Fast forward time by 600ms (so timeout is hit)
        testDispatcher.scheduler.advanceTimeBy(600L)
        testDispatcher.scheduler.runCurrent()

        val result = resultDeferred.await()

        // Only FastProvider is combined, SlowProvider was timed out
        assertTrue(result.explanation.contains("FastProvider=suspicious"))
        assertTrue(!result.explanation.contains("SlowProvider"))
    }
}
