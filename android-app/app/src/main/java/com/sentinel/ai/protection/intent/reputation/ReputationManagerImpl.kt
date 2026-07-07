package com.sentinel.ai.protection.intent.reputation

import com.sentinel.ai.core.coroutines.DispatcherProvider
import com.sentinel.ai.core.model.ScanResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.jvm.JvmSuppressWildcards

@Singleton
class ReputationManagerImpl @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards ReputationProvider>,
    private val combiner: EvidenceCombiner,
    private val dispatcherProvider: DispatcherProvider,
    private val config: ReputationConfig
) : ReputationManager {

    override suspend fun enrich(
        heuristicResult: ScanResult,
        target: ReputationTarget?
    ): ScanResult {
        if (target == null || providers.isEmpty()) {
            return heuristicResult
        }

        val evidence = coroutineScope {
            providers.asSequence()
                .filter { it.supports(target) }
                .map { provider ->
                    async(dispatcherProvider.io) {
                        runCatching {
                            withTimeoutOrNull(config.lookupTimeoutMs) {
                                provider.evaluate(target)
                            }
                        }.getOrNull()
                    }
                }
                .toList()
                .awaitAll()
                .filterNotNull()
        }

        return combiner.combine(heuristicResult, evidence)
    }
}
