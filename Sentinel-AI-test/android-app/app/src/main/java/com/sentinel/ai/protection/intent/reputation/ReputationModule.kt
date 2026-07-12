package com.sentinel.ai.protection.intent.reputation

import com.sentinel.ai.BuildConfig
import com.sentinel.ai.core.coroutines.DispatcherProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReputationModule {

    @Binds
    @Singleton
    abstract fun bindReputationManager(
        impl: ReputationManagerImpl
    ): ReputationManager

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindOpenPhishReputationProvider(
        impl: OpenPhishReputationProvider
    ): ReputationProvider

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindVirusTotalReputationProvider(
        impl: VirusTotalReputationProvider
    ): ReputationProvider

    companion object {
        @Provides
        @Singleton
        fun provideReputationConfig(): ReputationConfig {
            return ReputationConfig(
                openPhishFeedUrl = BuildConfig.OPENPHISH_FEED_URL,
                openPhishApiKey = BuildConfig.OPENPHISH_API_KEY,
                virusTotalApiKey = BuildConfig.VIRUSTOTAL_API_KEY,
                virusTotalLookupUrl = BuildConfig.VIRUSTOTAL_LOOKUP_URL,
                lookupTimeoutMs = BuildConfig.REPUTATION_LOOKUP_TIMEOUT_MS.toLongOrNull() ?: 10_000L
            )
        }

        @Provides
        @IntoSet
        @Singleton
        fun provideMockReputationProvider(): ReputationProvider = MockReputationProvider()
    }
}
