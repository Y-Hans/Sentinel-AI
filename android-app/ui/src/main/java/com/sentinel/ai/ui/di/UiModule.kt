package com.sentinel.ai.ui.di

import com.sentinel.ai.ui.components.DefaultSecurityTipProvider
import com.sentinel.ai.ui.components.SecurityTipProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class UiModule {
    @Binds
    abstract fun bindSecurityTipProvider(
        impl: DefaultSecurityTipProvider
    ): SecurityTipProvider
}
