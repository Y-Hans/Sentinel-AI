package com.sentinel.ai.warning

import com.sentinel.ai.core.warning.WarningNotificationDispatcher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WarningModule {

    @Binds
    @Singleton
    abstract fun bindWarningNotificationDispatcher(
        helper: WarningNotificationHelper
    ): WarningNotificationDispatcher
}
