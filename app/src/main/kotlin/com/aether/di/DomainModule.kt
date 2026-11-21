package com.aether.di

import android.content.Context
import com.aether.domain.llm.LlmClient
import com.aether.domain.memory.ClassifyMemories
import com.aether.domain.memory.MemoryRepository
import com.aether.domain.policy.DefaultRestraintPolicy
import com.aether.domain.policy.RestraintPolicy
import com.aether.tools.notification.NotificationPromptTool
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    @Provides
    fun provideClassifyMemories(
        repository: MemoryRepository,
        llmClient: LlmClient,
    ): ClassifyMemories = ClassifyMemories(repository, llmClient)

    @Provides
    @Singleton
    fun provideRestraintPolicy(): RestraintPolicy = DefaultRestraintPolicy()

    @Provides
    @Singleton
    fun provideNotificationPromptTool(
        @ApplicationContext context: Context,
    ): NotificationPromptTool = NotificationPromptTool(context)

    @Provides
    fun provideProcessFrictionNudge(
        threadRepository: com.aether.domain.thread.TrackedThreadRepository,
        actionRepository: com.aether.domain.action.ActionRecordRepository,
        restraintPolicy: RestraintPolicy,
        decisionPort: com.aether.domain.decision.DecisionPort,
        restraintConfigRepository: com.aether.domain.policy.RestraintConfigRepository,
    ): com.aether.domain.action.ProcessFrictionNudge {
        return com.aether.domain.action.ProcessFrictionNudge(
            threadRepository = threadRepository,
            actionRepository = actionRepository,
            restraintPolicy = restraintPolicy,
            decisionPort = decisionPort,
            restraintConfigRepository = restraintConfigRepository,
        )
    }
}
