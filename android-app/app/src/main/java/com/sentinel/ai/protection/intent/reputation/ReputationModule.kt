package com.sentinel.ai.protection.intent.reputation

import dagger.Binds
import dagger.Module
import dagger.Provides
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
}
