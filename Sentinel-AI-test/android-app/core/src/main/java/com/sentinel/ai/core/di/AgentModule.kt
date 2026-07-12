package com.sentinel.ai.core.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Placeholder for agent coordinator bindings.
 * Coordinator providers will be registered here when the agents module is wired.
 */
@Module
@InstallIn(SingletonComponent::class)
object AgentModule
