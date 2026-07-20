package com.aether.di

import com.aether.domain.llm.LlmClient
import com.aether.domain.memory.ClassifyMemories
import com.aether.domain.memory.MemoryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Constructs the domain's use cases.
 *
 * They are assembled here rather than annotated with `@Inject` because
 * `:domain` depends on nothing but Kotlin and coroutines — pulling
 * `javax.inject` into it would make the layer that must stay framework-free
 * depend on a framework. The cost is one `@Provides` per use case, in the only
 * module that already knows about every layer.
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    @Provides
    fun provideClassifyMemories(
        repository: MemoryRepository,
        llmClient: LlmClient,
    ): ClassifyMemories = ClassifyMemories(repository, llmClient)
}
