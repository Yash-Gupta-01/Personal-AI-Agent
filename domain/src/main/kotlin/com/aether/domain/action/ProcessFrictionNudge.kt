package com.aether.domain.action

import com.aether.domain.decision.DecisionAnswer
import com.aether.domain.decision.DecisionPort
import com.aether.domain.decision.DecisionQuestion
import com.aether.domain.policy.RestraintConfig
import com.aether.domain.policy.RestraintConfigRepository
import com.aether.domain.policy.RestraintPolicy
import com.aether.domain.policy.RestraintVerdict
import com.aether.domain.thread.TrackedThread
import com.aether.domain.thread.TrackedThreadRepository
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
) {

    enum class RoutineEventType {
        FRICTION,
        SUCCESS,
        OBSTACLE,
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
        val event = resolveRoutineEvent(content)
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
                // Point 1.B: Success resets misses and updates streak silently in SQLite!
                val updated = thread.recordSuccess(now)
                threadRepository.save(updated)
                return null
            }
            RoutineEventType.OBSTACLE -> {
                // Point 1.C: External obstacle (illness, closed facility) -> no penalty, no alarm!
                val updated = thread.copy(lastObservedAt = now, updatedAt = now)
                threadRepository.save(updated)
                return null
            }
            RoutineEventType.FRICTION -> {
                val updatedThread = thread.recordMiss(now)
                threadRepository.save(updatedThread)

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

                val hasSleepDebt = updatedThread.knownBlockers.contains("sleep_debt")
                val hour: Int
                val minute: Int
                val label: String
                val nudgeTitle: String
                val nudgeBody: String
                val confirmText: String

                if (hasSleepDebt) {
                    hour = 22
                    minute = 30
                    label = "Wind-Down"
                    nudgeTitle = "Bedtime Wind-Down"
                    nudgeBody = "Since lack of sleep held you back from the gym, would a 22:30 wind-down reminder help you get to bed earlier tonight?"
                    confirmText = "Set 22:30 Wind-Down"
                } else {
                    hour = 7
                    minute = 0
                    label = "Gym"
                    nudgeTitle = "Morning Gym Routine"
                    nudgeBody = "I noticed you missed the gym today. Would a 07:00 alarm help get you there tomorrow?"
                    confirmText = "Set 07:00 Alarm"
                }

                val proposedAction = ActionRecord.propose(
                    threadId = updatedThread.id,
                    toolName = "set_alarm",
                    toolParams = mapOf(
                        "hour" to hour.toString(),
                        "minute" to minute.toString(),
                        "label" to label,
                        "nudge_title" to nudgeTitle,
                        "nudge_body" to nudgeBody,
                        "confirm_button_text" to confirmText,
                    ),
                    now = now,
                )
                actionRepository.save(proposedAction)
                return proposedAction
            }
            RoutineEventType.NONE -> return null
        }
    }

    private suspend fun resolveRoutineEvent(content: String): RoutineEvent {
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

        return detectEventHeuristic(content)
    }

    private fun detectEventHeuristic(content: String): RoutineEvent {
        val lower = content.lowercase()

        // 1. Resolve Topic
        val topic = when {
            lower.contains("gym") || lower.contains("workout") || lower.contains("run") || lower.contains("exercise") || lower.contains("steps") -> "gym"
            lower.contains("sleep") || lower.contains("bed") || lower.contains("woke up") || lower.contains("alarm") || lower.contains("insomnia") || lower.contains("reddit") -> "sleep"
            lower.contains("report") || lower.contains("standup") || lower.contains("deadline") || lower.contains("sprint") || lower.contains("meeting") || lower.contains("presentation") -> "work"
            lower.contains("medicine") || lower.contains("lunch") || lower.contains("vitamins") || lower.contains("water") || lower.contains("breakfast") || lower.contains("headache") || lower.contains("meditated") -> "health"
            else -> "none"
        }

        if (topic == "none") {
            return RoutineEvent(RoutineEventType.NONE, "none")
        }

        // 2. Resolve Event Type
        val isSuccess = lower.contains("crushed") || lower.contains("solid deep sleep") ||
                lower.contains("finished all sprint") || lower.contains("woke up on time") ||
                lower.contains("completed") || lower.contains("meditated") ||
                lower.contains("hit all my protein") || lower.contains("hit 10000 steps") ||
                lower.contains("feeling great") || lower.contains("deliberately took a rest day")

        if (isSuccess) {
            return RoutineEvent(RoutineEventType.SUCCESS, topic)
        }

        val isObstacle = lower.contains("fever") || lower.contains("closed today") ||
                lower.contains("cancelled") || lower.contains("construction outside") ||
                lower.contains("extended") || lower.contains("urgent assignment")

        if (isObstacle) {
            return RoutineEvent(RoutineEventType.OBSTACLE, topic)
        }

        val isFriction = lower.contains("didn't") || lower.contains("did not") ||
                lower.contains("couldn't") || lower.contains("could not") ||
                lower.contains("missed") || lower.contains("skip") ||
                lower.contains("unable") || lower.contains("failed") ||
                lower.contains("late") || lower.contains("terrible") ||
                lower.contains("exhausted") || lower.contains("forgot") ||
                lower.contains("headache") || lower.contains("lazy") ||
                lower.contains("groggy") || lower.contains("scrolling") ||
                lower.contains("fell back asleep")

        if (isFriction) {
            return RoutineEvent(RoutineEventType.FRICTION, topic)
        }

        return RoutineEvent(RoutineEventType.NONE, topic)
    }

    private fun topicTitle(topic: String): String = when (topic) {
        "gym" -> "Morning Gym Routine"
        "sleep" -> "Bedtime Wind-Down"
        else -> "Daily Routine"
    }
}
