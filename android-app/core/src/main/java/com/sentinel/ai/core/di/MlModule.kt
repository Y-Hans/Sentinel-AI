package com.sentinel.ai.core.di

import android.content.Context
import com.sentinel.ai.core.ml.messages.MessageScanner
import com.sentinel.ai.core.ml.url.UrlScanner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MlModule {

    @Provides
    @Singleton
    fun provideUrlScanner(
        @ApplicationContext context: Context
    ): UrlScanner {
        return try {
            context.assets.open("v7_champion_portable.json").use { stream ->
                UrlScanner.fromInputStream(stream)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load URL-ML Champion V7 model from assets")
            throw e
        }
    }

    @Provides
    @Singleton
    fun provideMessageScanner(
        @ApplicationContext context: Context
    ): MessageScanner {
        return try {
            val wordStream = context.assets.open("champion_v2_word_vocab_idf.json")
            val charStream = context.assets.open("champion_v2_char_vocab_idf.json")
            val scalerStream = context.assets.open("champion_v2_scaler.json")
            val treesStream = context.assets.open("champion_v2_trees.json")

            MessageScanner.createFromStreams(
                wordStream = wordStream,
                charStream = charStream,
                scalerStream = scalerStream,
                treesStream = treesStream,
                threshold = 0.704f
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to load Messages-ML Champion V2 model from assets")
            throw e
        }
    }
}
