package com.aether.llm.di

import android.content.Context
import com.aether.domain.llm.LlmClient
import com.aether.llm.LlamaCppClient
import com.aether.llm.ModelDownloader
import com.aether.llm.ModelStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

/**
 * Binds the domain's inference port to llama.cpp.
 *
 * The single place the backend is named. Switching to another runtime — the
 * LiteRT-LM fallback, for instance — means changing the type provided here and
 * nothing else in the app.
 */
@Module
@InstallIn(SingletonComponent::class)
object LlmModule {

    @Provides
    @Singleton
    fun provideModelStore(@ApplicationContext context: Context): ModelStore =
        ModelStore(context)

    @Provides
    @Singleton
    fun provideModelDownloader(
        @ApplicationContext context: Context,
        modelStore: ModelStore,
    ): ModelDownloader = ModelDownloader(context, modelStore)

    @Provides
    @Singleton
    fun provideLlmClient(modelStore: ModelStore): LlmClient =
        // Dispatchers.Default, not IO: inference is CPU-bound and saturates the
        // performance cores. Putting it on the IO pool would let it compete
        // with an unbounded number of blocking tasks.
        LlamaCppClient(modelStore, Dispatchers.Default)
}
