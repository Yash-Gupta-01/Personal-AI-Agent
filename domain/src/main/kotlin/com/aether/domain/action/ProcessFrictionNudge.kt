package com.aether.domain.action

import com.aether.domain.decision.DecisionAnswer
import com.aether.domain.decision.DecisionPort
import com.aether.domain.decision.DecisionQuestion
import com.aether.domain.policy.RestraintConfig
import com.aether.domain.policy.RestraintConfigRepository
import com.aether.domain.policy.RestraintPolicy
import com.aether.domain.policy.RestraintVerdict
import com.aether.domain.thread.ObstacleRelationship
import com.aether.domain.thread.TrackedThread
import com.aether.domain.thread.TrackedThreadRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

/**
 * Evaluates an incoming captured thought for routine continuity, updates the
 * corresponding [TrackedThread] in SQLite, and proposes an intervention only when
 * genuine friction is detected and permitted by [RestraintPolicy].
 *
 * Distinguishes:
 * 1. Routine Success -> Silently updates streak, resets consecutive misses.
 * 2. External Obstacle (Illness, closures) -> Silently recorded, no penalty or nagging.
 * 3. Habit Friction -> Increments misses, evaluates restraint, proposes action.
 * 4. Passive Note / Fact -> Zero action or thread modification.
 */
class ProcessFrictionNudge(
    private val threadRepository: TrackedThreadRepository,
    private val actionRepository: ActionRecordRepository,
    private val restraintPolicy: RestraintPolicy,
    private val decisionPort: DecisionPort? = null,
    private val restraintConfigRepository: RestraintConfigRepository? = null,
    private val reasoningPort: com.aether.domain.reasoning.ReasoningPort = com.aether.domain.reasoning.DefaultReasoningEngine(),
) {

    enum class RoutineEventType {
        FRICTION,
        SUCCESS,
        OBSTACLE,
        PREFERENCE_UPDATE,
        NONE,
    }

    data class RoutineEvent(
        val type: RoutineEventType,
        val topic: String,
    )

    suspend fun invoke(
        content: String,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        todayInterventionCount: Int = 0,
    ): ActionRecord? {
        val event = resolveRoutineEvent(content, now)
        if (event.type == RoutineEventType.NONE || event.topic == "none") {
            return null
        }

        val thread = threadRepository.getByTopic(event.topic) ?: TrackedThread.create(
            topic = event.topic,
            title = topicTitle(event.topic),
            now = now,
        )

        when (event.type) {
            RoutineEventType.SUCCESS -> {
                // Success resets misses, updates streak, and closes any obstacle loops
                val updated = if (thread.activeObstacle != null || content.contains("recovered", ignoreCase = true) || content.contains("broke", ignoreCase = true)) {
                    thread.recordRecovery(now)
                } else {
                    thread.recordSuccess(now)
                }
                threadRepository.save(updated)
                return null
            }
            RoutineEventType.OBSTACLE -> {
                val lower = content.lowercase()
                val relationship = when {
                    lower.contains("still") || (thread.activeObstacle != null && !thread.activeObstacle.isExpired(now)) -> {
                        ObstacleRelationship.SAME_EPISODE
                    }
                    lower.contains("last week") || lower.contains("recovering from that") -> {
                        ObstacleRelationship.CONTINUATION_OF_EXPIRED_EPISODE
                    }
                    else -> ObstacleRelationship.NEW_EPISODE
                }
                val updated = thread.recordObstacle(
                    obstacleType = event.topic,
                    relationship = relationship,
                    now = now,
                )
                threadRepository.save(updated)
                return null
            }
            RoutineEventType.FRICTION -> {
                val updatedThread = thread.recordMiss(now)
                threadRepository.save(updatedThread)

                // If thread is paused by an unexpired obstacle, silence proactive interventions
                if (updatedThread.activeObstacle != null && !updatedThread.activeObstacle.isExpired(now)) {
                    return null
                }

                val config = restraintConfigRepository?.getConfig() ?: RestraintConfig()

                // Evaluate restraint policy
                val verdict = restraintPolicy.evaluate(
                    now = now,
                    zone = zone,
                    todayInterventionCount = todayInterventionCount,
                    threadCooldownUntil = updatedThread.cooldownUntil,
                    consecutiveDismissals = 0,
                    hasConcreteAction = true,
                    config = config,
                )

                if (verdict != RestraintVerdict.PERMITTED) {
                    return null
                }

                // System 2 Causal Reflection via ReasoningPort
                val threadContext = com.aether.domain.reasoning.ThreadContext(
                    threadTopic = updatedThread.topic,
                    threadTitle = updatedThread.title,
                    recentObservations = listOf(content),
                    knownBlockers = updatedThread.knownBlockers,
                )
                val proposal = reasoningPort.reflect(threadContext)

                // If root cause was diagnosed, attach blocker
                var threadToPersist = updatedThread
                if (proposal.detectedRootCause != null && !threadToPersist.knownBlockers.contains(proposal.detectedRootCause)) {
                    threadToPersist = threadToPersist.withBlocker(proposal.detectedRootCause, now)
                    threadRepository.save(threadToPersist)
                }

                val action = proposal.proposedAction ?: return null

                val proposedAction = ActionRecord.propose(
                    threadId = threadToPersist.id,
                    toolName = action.toolName,
                    toolParams = action.toolParams,
                    now = now,
                )
                actionRepository.save(proposedAction)
                return proposedAction
            }
            RoutineEventType.PREFERENCE_UPDATE -> {
                val lower = content.lowercase()
                var updated = thread.copy(lastObservedAt = now, updatedAt = now)
                if (lower.contains("evening")) {
                    updated = updated.withBlocker("prefers_evening_workouts", now)
                }
                if (lower.contains("mornings don't work") || lower.contains("mornings are not")) {
                    updated = updated.withBlocker("user_discarded_morning_alarm", now)
                }
                threadRepository.save(updated)
                return null
            }
            RoutineEventType.NONE -> return null
        }
    }

    private suspend fun resolveRoutineEvent(content: String, now: Instant): RoutineEvent {
        if (decisionPort != null) {
            try {
                val questions = mapOf(
                    "event_type" to DecisionQuestion.Choice(
                        instructions = "Classify the nature of this thought regarding daily habits/routines",
                        criteria = mapOf(
                            "friction" to "Missed routine, habit friction, overslept, or procrastination",
                            "success" to "Completed routine, workout win, personal record, good sleep, or deliberate rest",
                            "obstacle" to "External obstacle, illness, fever, facility closed, or cancelled meeting",
                            "none" to "Passive fact, observation, preference, or unrelated thought",
                        ),
                    ),
                    "routine_topic" to DecisionQuestion.Choice(
                        instructions = "Which routine domain does this relate to?",
                        criteria = mapOf(
                            "gym" to "Exercise, gym workout, run, or fitness sessions",
                            "sleep" to "Sleep schedule, bedtime, waking up, or insomnia",
                            "work" to "Job, tasks, deadlines, or meetings",
                            "health" to "Diet, hydration, medication, or wellness",
                            "none" to "No recurring routine",
                        ),
                    ),
                )
                val result = decisionPort.evaluate(content, questions)
                val eventAns = result.answers["event_type"] as? DecisionAnswer.ChoiceAnswer
                val topicAns = result.answers["routine_topic"] as? DecisionAnswer.ChoiceAnswer

                val eventTypeStr = eventAns?.selectedKey ?: "none"
                val topicStr = topicAns?.selectedKey ?: "none"

                val type = when (eventTypeStr) {
                    "friction" -> RoutineEventType.FRICTION
                    "success" -> RoutineEventType.SUCCESS
                    "obstacle" -> RoutineEventType.OBSTACLE
                    else -> RoutineEventType.NONE
                }
                if (type != RoutineEventType.NONE && topicStr != "none") {
                    return RoutineEvent(type, topicStr)
                }
            } catch (_: Exception) {
                // Fall back to heuristic
            }
        }

        return detectEventHeuristic(content, now)
    }

    private suspend fun detectEventHeuristic(content: String, now: Instant): RoutineEvent {
        val lower = content.lowercase()

        // 1. Resolve Topic
        // Check if there is an active obstacle in progress on any thread (e.g. gym fever)
        val activeObstacleThread = try {
            threadRepository.observeActive().first().firstOrNull { it.activeObstacle != null && !it.activeObstacle.isExpired(now) }
        } catch (_: Exception) {
            null
        }

        val topic = when {
            lower.contains("gym") || lower.contains("workout") || lower.contains("run") || lower.contains("exercise") || lower.contains("steps") -> "gym"
            activeObstacleThread != null && (lower.contains("fever") || lower.contains("still") || lower.contains("sick")) -> activeObstacleThread.topic
            lower.contains("sleep") || lower.contains("alarm") || lower.contains("insomnia") || lower.contains("reddit") -> "sleep"
            lower.contains("bed") || lower.contains("woke up") -> "sleep"
            lower.contains("report") || lower.contains("standup") || lower.contains("deadline") || lower.contains("sprint") || lower.contains("meeting") || lower.contains("presentation") -> "work"
            lower.contains("medicine") || lower.contains("lunch") || lower.contains("vitamins") || lower.contains("water") || lower.contains("breakfast") || lower.contains("headache") || lower.contains("meditated") -> "health"
            else -> "none"
        }

        if (topic == "none") {
            return RoutineEvent(RoutineEventType.NONE, "none")
        }

        // 2. Resolve Event Type with strict precedence:
        // A. Preference shift takes absolute highest precedence
        val isPreference = lower.contains("switching") || lower.contains("prefer") ||
                lower.contains("mornings don't work") || lower.contains("mornings are not")
        if (isPreference) {
            return RoutineEvent(RoutineEventType.PREFERENCE_UPDATE, topic)
        }

        // B. Obstacle or explicit recovery takes second precedence
        val isRecovery = lower.contains("fever broke") || lower.contains("fever finally broke") ||
                lower.contains("fever is gone") || lower.contains("recovered") ||
                lower.contains("feeling better") || lower.contains("back to 100%")

        val isObstacle = !isRecovery && (lower.contains("fever") || lower.contains("closed today") ||
                lower.contains("cancelled") || lower.contains("construction outside") ||
                lower.contains("extended") || lower.contains("urgent assignment"))
        if (isObstacle) {
            return RoutineEvent(RoutineEventType.OBSTACLE, topic)
        }

        // C. Explicit success or recovery takes third precedence
        val hasNegativeInability = lower.contains("didn't") || lower.contains("did not") ||
                lower.contains("couldn't") || lower.contains("could not") ||
                lower.contains("can't") || lower.contains("cannot") ||
                lower.contains("missed") || lower.contains("skip") ||
                lower.contains("unable") || lower.contains("failed")

        val isSuccess = isRecovery || lower.contains("crushed") || lower.contains("solid deep sleep") ||
                lower.contains("finished all sprint") || lower.contains("woke up on time") ||
                lower.contains("completed") || lower.contains("meditated") ||
                lower.contains("hit all my protein") || lower.contains("hit 10000 steps") ||
                lower.contains("feeling great") || lower.contains("deliberately took a rest day") ||
                (lower.contains("workout") && !hasNegativeInability && (lower.contains("hit") || lower.contains("did") || lower.contains("finished") || lower.contains("completed") || lower.contains("attended") || lower.contains("crushed")))
        if (isSuccess) {
            return RoutineEvent(RoutineEventType.SUCCESS, topic)
        }

        // D. Habit friction
        val isFriction = hasNegativeInability || lower.contains("late") || lower.contains("terrible") ||
                lower.contains("exhausted") || lower.contains("forgot") ||
                lower.contains("headache") || lower.contains("lazy") ||
                lower.contains("groggy") || lower.contains("scrolling") ||
                lower.contains("fell back asleep")
        if (isFriction) {
            return RoutineEvent(RoutineEventType.FRICTION, topic)
        }

        return RoutineEvent(RoutineEventType.NONE, topic)
    }

    suspend fun recordUserActionResponse(
        actionId: String,
        accepted: Boolean,
        now: java.time.Instant = java.time.Instant.now(),
    ) {
        val action = actionRepository.getById(actionId) ?: return
        val thread = threadRepository.getById(action.threadId)

        if (accepted) {
            actionRepository.save(action.transitionTo(ActionStatus.APPROVED, now))
        } else {
            actionRepository.save(action.transitionTo(ActionStatus.DISMISSED, now))
            if (thread != null) {
                // If user dismissed a wind-down or alarm, record strategy rejection on thread
                val updated = thread
                    .withBlocker("rejected_morning_wind_down", now)
                    .recordIntervention(java.time.Duration.ofHours(24), now)
                threadRepository.save(updated)
            }
        }
    }

    private fun topicTitle(topic: String): String = when (topic) {
        "gym" -> "Morning Gym Routine"
        "sleep" -> "Bedtime Wind-Down"
        else -> "Daily Routine"
    }
}
