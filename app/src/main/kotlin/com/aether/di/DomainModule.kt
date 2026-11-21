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
    @Singleton
    fun provideReasoningPort(): com.aether.domain.reasoning.ReasoningPort =
        com.aether.domain.reasoning.DefaultReasoningEngine()

    @Provides
    fun provideProcessFrictionNudge(
        threadRepository: com.aether.domain.thread.TrackedThreadRepository,
        actionRepository: com.aether.domain.action.ActionRecordRepository,
        restraintPolicy: RestraintPolicy,
        decisionPort: com.aether.domain.decision.DecisionPort,
        restraintConfigRepository: com.aether.domain.policy.RestraintConfigRepository,
        reasoningPort: com.aether.domain.reasoning.ReasoningPort,
    ): com.aether.domain.action.ProcessFrictionNudge {
        return com.aether.domain.action.ProcessFrictionNudge(
            threadRepository = threadRepository,
            actionRepository = actionRepository,
            restraintPolicy = restraintPolicy,
            decisionPort = decisionPort,
            restraintConfigRepository = restraintConfigRepository,
            reasoningPort = reasoningPort,
        )
    }

    @Provides
    fun provideExecuteActionUseCase(
        actionRepository: com.aether.domain.action.ActionRecordRepository,
    ): com.aether.domain.action.ExecuteActionUseCase =
        com.aether.domain.action.ExecuteActionUseCase(actionRepository)

    @Provides
    fun provideDismissActionUseCase(
        actionRepository: com.aether.domain.action.ActionRecordRepository,
        threadRepository: com.aether.domain.thread.TrackedThreadRepository,
    ): com.aether.domain.action.DismissActionUseCase =
        com.aether.domain.action.DismissActionUseCase(actionRepository, threadRepository)

    @Provides
    fun provideEvaluateFollowUpUseCase(
        actionRepository: com.aether.domain.action.ActionRecordRepository,
        threadRepository: com.aether.domain.thread.TrackedThreadRepository,
    ): com.aether.domain.action.EvaluateFollowUpUseCase =
        com.aether.domain.action.EvaluateFollowUpUseCase(actionRepository, threadRepository)

    @Provides
    fun provideProcessAmbientSignalUseCase(
        threadRepository: com.aether.domain.thread.TrackedThreadRepository,
        actionRepository: com.aether.domain.action.ActionRecordRepository,
        restraintPolicy: RestraintPolicy,
        restraintConfigRepository: com.aether.domain.policy.RestraintConfigRepository,
    ): com.aether.domain.signal.ProcessAmbientSignalUseCase =
        com.aether.domain.signal.ProcessAmbientSignalUseCase(
            threadRepository,
            actionRepository,
            restraintPolicy,
            restraintConfigRepository,
        )
}
