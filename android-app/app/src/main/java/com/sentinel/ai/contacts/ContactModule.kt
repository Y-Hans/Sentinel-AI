package com.sentinel.ai.contacts

import com.sentinel.ai.core.sender.ContactResolver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ContactModule {

    @Binds
    @Singleton
    abstract fun bindContactResolver(impl: AndroidContactResolver): ContactResolver
}
