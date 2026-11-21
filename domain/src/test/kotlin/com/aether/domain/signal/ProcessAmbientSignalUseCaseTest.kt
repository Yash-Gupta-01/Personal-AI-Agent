package com.aether.domain.signal

import com.aether.domain.action.ActionRecord
import com.aether.domain.action.ActionRecordRepository
import com.aether.domain.action.ActionStatus
import com.aether.domain.policy.DefaultRestraintPolicy
import com.aether.domain.policy.RestraintConfig
import com.aether.domain.policy.RestraintConfigRepository
import com.aether.domain.thread.TrackedThread
import com.aether.domain.thread.TrackedThreadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class ProcessAmbientSignalUseCaseTest {

    private val fixedNow = Instant.parse("2026-11-21T07:30:00Z")
    private val utcZone = ZoneId.of("UTC")

    private val actionMap = mutableMapOf<String, ActionRecord>()
    private val threadMap = mutableMapOf<String, TrackedThread>()

    private val actionRepo = object : ActionRecordRepository {
        override fun observePending(): Flow<List<ActionRecord>> = flowOf(emptyList())
        override fun observePendingFollowUps(): Flow<List<ActionRecord>> = flowOf(emptyList())
        override fun observeForThread(threadId: String): Flow<List<ActionRecord>> = flowOf(emptyList())
        override suspend fun getById(id: String): ActionRecord? = actionMap[id]
        override suspend fun getLatestPendingFollowUp(): ActionRecord? =
            actionMap.values.filter { it.status == ActionStatus.EXECUTED || it.status == ActionStatus.OBSERVED }
                .maxByOrNull { it.executedAt ?: Instant.EPOCH }
        override suspend fun save(record: ActionRecord) { actionMap[record.id] = record }
        override suspend fun delete(id: String) { actionMap.remove(id) }
    }

    private val threadRepo = object : TrackedThreadRepository {
        override fun observeActive(): Flow<List<TrackedThread>> = flowOf(threadMap.values.toList())
        override suspend fun getById(id: String): TrackedThread? = threadMap[id]
        override suspend fun getByTopic(topic: String): TrackedThread? = threadMap.values.firstOrNull { it.topic == topic }
        override suspend fun save(thread: TrackedThread) { threadMap[thread.id] = thread }
        override suspend fun delete(id: String) { threadMap.remove(id) }
    }

    private val restraintRepo = object : RestraintConfigRepository {
        override fun observeConfig(): Flow<RestraintConfig> = flowOf(RestraintConfig(quietHoursEnabled = false))
        override suspend fun getConfig(): RestraintConfig = RestraintConfig(quietHoursEnabled = false)
        override suspend fun updateConfig(config: RestraintConfig) {}
    }

    private lateinit var useCase: ProcessAmbientSignalUseCase

    @Before
    fun setUp() {
        actionMap.clear()
        threadMap.clear()
        useCase = ProcessAmbientSignalUseCase(
            threadRepository = threadRepo,
            actionRepository = actionRepo,
            restraintPolicy = DefaultRestraintPolicy(),
            restraintConfigRepository = restraintRepo,
        )
    }

    @Test
    fun `alarm dismissed triggers check-in prompt for executed action`() = runTest {
        val action = ActionRecord.propose("thread-gym", "set_alarm", mapOf("hour" to "7"), fixedNow, "act-1")
            .transitionTo(ActionStatus.EXECUTED, fixedNow)
        actionMap["act-1"] = action

        val outcome = useCase.invoke(AmbientSignal.AlarmDismissed(fixedNow, label = "Gym"), utcZone)
        assertTrue(outcome is ProcessAmbientSignalUseCase.SignalOutcome.CheckInPrompt)
        assertEquals(ActionStatus.OBSERVED, actionMap["act-1"]?.status)
    }

    @Test
    fun `morning unlock triggers check-in prompt when action is pending`() = runTest {
        val action = ActionRecord.propose("thread-gym", "set_alarm", mapOf("hour" to "7"), fixedNow, "act-1")
            .transitionTo(ActionStatus.EXECUTED, fixedNow)
        actionMap["act-1"] = action

        val outcome = useCase.invoke(AmbientSignal.UserPresent(fixedNow, LocalTime.of(7, 30)), utcZone)
        assertTrue(outcome is ProcessAmbientSignalUseCase.SignalOutcome.CheckInPrompt)
        assertEquals(ActionStatus.OBSERVED, actionMap["act-1"]?.status)
    }

    @Test
    fun `late night unlock at 02_30 records night wakefulness blocker on sleep thread`() = runTest {
        val nightTime = Instant.parse("2026-11-21T02:30:00Z")
        val outcome = useCase.invoke(AmbientSignal.UserPresent(nightTime, LocalTime.of(2, 30)), utcZone)
        assertTrue(outcome is ProcessAmbientSignalUseCase.SignalOutcome.NightWakeRecorded)

        val sleepThread = threadMap.values.firstOrNull { it.topic == "sleep" }
        assertTrue(sleepThread?.knownBlockers?.contains("late_night_wakefulness") == true)
    }
}
