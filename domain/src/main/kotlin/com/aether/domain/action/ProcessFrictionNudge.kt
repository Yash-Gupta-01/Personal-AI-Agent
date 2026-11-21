package com.aether.domain.action

import com.aether.domain.decision.DecisionAnswer
import com.aether.domain.decision.DecisionPort
import com.aether.domain.decision.DecisionQuestion
import com.aether.domain.policy.RestraintPolicy
import com.aether.domain.policy.RestraintVerdict
import com.aether.domain.thread.TrackedThread
import com.aether.domain.thread.TrackedThreadRepository
import java.time.Instant
import java.time.ZoneId

/**
 * Evaluates an incoming captured thought for routine friction, updates the
 * corresponding [TrackedThread], applies [RestraintPolicy], and proposes a concrete
 * device action when permitted.
 *
 * Employs System 1 [DecisionPort] when available, falling back to deterministic
 * operational heuristics.
 */
class ProcessFrictionNudge(
    private val threadRepository: TrackedThreadRepository,
    private val actionRepository: ActionRecordRepository,
    private val restraintPolicy: RestraintPolicy,
    private val decisionPort: DecisionPort? = null,
) {

    suspend fun invoke(
        content: String,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): ActionRecord? {
        val topic = resolveFrictionTopic(content) ?: return null

        val thread = threadRepository.getByTopic(topic) ?: TrackedThread.create(
            topic = topic,
            title = topicTitle(topic),
            now = now,
        )

        val updatedThread = thread.recordMiss(now)
        threadRepository.save(updatedThread)

        // Evaluate restraint policy
        val verdict = restraintPolicy.evaluate(
            now = now,
            zone = zone,
            todayInterventionCount = 0,
            threadCooldownUntil = updatedThread.cooldownUntil,
            consecutiveDismissals = 0,
            hasConcreteAction = true,
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

    private suspend fun resolveFrictionTopic(content: String): String? {
        if (decisionPort != null) {
            try {
                val questions = mapOf(
                    "is_friction" to DecisionQuestion.Noul(
                        "Does this capture indicate a missed commitment or recurring daily routine friction?",
                    ),
                    "routine_choice" to DecisionQuestion.Choice(
                        instructions = "Which routine thread does this capture link to?",
                        criteria = mapOf(
                            "gym" to "Exercise, gym workout, or fitness session",
                            "sleep" to "Sleep debt, bedtime routine, or waking up late",
                            "none" to "No recurring routine; general note or fact",
                        ),
                    ),
                )
                val result = decisionPort.evaluate(content, questions)
                val frictionAns = result.answers["is_friction"] as? DecisionAnswer.NoulAnswer
                val choiceAns = result.answers["routine_choice"] as? DecisionAnswer.ChoiceAnswer

                if (frictionAns != null && frictionAns.isTrue && frictionAns.confidence >= 0.2f) {
                    val selected = choiceAns?.selectedKey
                    if (selected != null && selected != "none") {
                        return selected
                    }
                }
            } catch (_: Exception) {
                // Fall back to deterministic heuristic on model failure/unavailability
            }
        }

        return detectFrictionTopicHeuristic(content)
    }

    private fun detectFrictionTopicHeuristic(content: String): String? {
        val lower = content.lowercase()
        return when {
            (lower.contains("gym") || lower.contains("workout") || lower.contains("exercise")) &&
                (lower.contains("miss") || lower.contains("skip") || lower.contains("didn't") || lower.contains("did not") || lower.contains("couldn't") || lower.contains("could not") || lower.contains("unable") || lower.contains("failed") || lower.contains("late")) -> "gym"
            else -> null
        }
    }

    private fun topicTitle(topic: String): String = when (topic) {
        "gym" -> "Morning Gym Routine"
        "sleep" -> "Bedtime Wind-Down"
        else -> "Daily Routine"
    }
}
