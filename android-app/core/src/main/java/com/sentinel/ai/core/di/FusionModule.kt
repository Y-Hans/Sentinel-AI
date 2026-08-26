package com.sentinel.ai.core.di

import com.sentinel.ai.core.fusion.DefaultRiskFusionEngine
import com.sentinel.ai.core.fusion.RiskFusionEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FusionModule {

    @Binds
    @Singleton
    abstract fun bindRiskFusionEngine(
        impl: DefaultRiskFusionEngine
    ): RiskFusionEngine
}
