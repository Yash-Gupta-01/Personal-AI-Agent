package com.aether.domain.reasoning

/**
 * Deterministic baseline implementation of [ReasoningPort].
 *
 * Performs multi-turn causal reasoning across tracked routine threads, diagnosing
 * second-order blockers (e.g. sleep debt blocking morning workouts, work overrun blocking
 * evening routines) and formulating concrete actionable tool proposals.
 */
class DefaultReasoningEngine : ReasoningPort {

    override suspend fun reflect(context: ThreadContext): ReasoningProposal {
        val topic = context.threadTopic.lowercase().trim()
        val blockers = context.knownBlockers.map { it.lowercase().trim() }
        val recentText = context.recentObservations.joinToString(" ").lowercase()

        // Check for explicit user rejection of morning strategy or preference pivot to evening
        val prefersEvening = blockers.contains("prefers_evening_workouts") ||
                recentText.contains("switching my workouts to evenings") ||
                recentText.contains("switching to evening") ||
                recentText.contains("evenings after work") ||
                recentText.contains("mornings don't work for me")

        val discardedMorningAlarm = blockers.contains("user_discarded_morning_alarm") ||
                blockers.contains("rejected_morning_wind_down")

        if (topic in listOf("gym", "workout", "fitness") && (prefersEvening || discardedMorningAlarm)) {
            // Respect user preference shift: suppress zombie morning alarm proposals
            return ReasoningProposal(
                detectedRootCause = if (prefersEvening) "prefers_evening_workouts" else "user_discarded_morning_alarm",
                proposedAction = null,
                reasoningSummary = "User preferred evening workouts or discarded morning strategy; suppressing morning alarm.",
            )
        }

        // 1. Diagnose Root Cause
        val detectedRootCause: String? = when {
            blockers.contains("sleep_debt") ||
                    recentText.contains("sleep") ||
                    recentText.contains("slept") ||
                    recentText.contains("overslept") ||
                    recentText.contains("wake up") ||
                    recentText.contains("tired") ||
                    recentText.contains("late") -> "sleep_debt"
            blockers.contains("work_overrun") || recentText.contains("work") || recentText.contains("meeting") || recentText.contains("deadline") -> "work_overrun"
            blockers.contains("physical_soreness") || blockers.contains("physical_limitation") || recentText.contains("sore") || recentText.contains("hurt") -> "physical_soreness"
            blockers.contains("late_night_wakefulness") || recentText.contains("scrolling") || recentText.contains("phone") -> "late_night_wakefulness"
            else -> blockers.firstOrNull()
        }

        // 2. Formulate Causal Intervention
        val proposedAction: ProposedAction? = when (topic) {
            "gym", "workout", "fitness" -> {
                when (detectedRootCause) {
                    "sleep_debt" -> ProposedAction(
                        toolName = "set_alarm",
                        toolParams = mapOf(
                            "hour" to "22",
                            "minute" to "30",
                            "label" to "Wind-Down",
                            "nudge_title" to "Bedtime Wind-Down",
                            "nudge_body" to "Since lack of sleep held you back from the gym, would a 22:30 wind-down reminder help you get to bed earlier tonight?",
                            "confirm_button_text" to "Set 22:30 Wind-Down",
                        ),
                        notificationTitle = "Bedtime Wind-Down",
                        notificationBody = "Since lack of sleep held you back from the gym, would a 22:30 wind-down reminder help you get to bed earlier tonight?",
                        confirmButtonText = "Set 22:30 Wind-Down",
                    )
                    "work_overrun" -> ProposedAction(
                        toolName = "set_alarm",
                        toolParams = mapOf(
                            "hour" to "17",
                            "minute" to "45",
                            "label" to "Gym Departure",
                            "nudge_title" to "Evening Gym Transition",
                            "nudge_body" to "Work ran late recently. Would a 17:45 transition alert help you step away for your workout today?",
                            "confirm_button_text" to "Set 17:45 Alert",
                        ),
                        notificationTitle = "Evening Gym Transition",
                        notificationBody = "Work ran late recently. Would a 17:45 transition alert help you step away for your workout today?",
                        confirmButtonText = "Set 17:45 Alert",
                    )
                    "physical_soreness" -> ProposedAction(
                        toolName = "set_alarm",
                        toolParams = mapOf(
                            "hour" to "08",
                            "minute" to "00",
                            "label" to "Mobility & Stretch",
                            "nudge_title" to "Active Recovery Session",
                            "nudge_body" to "You mentioned feeling sore. Would a gentle 08:00 mobility and stretch session fit better than high intensity today?",
                            "confirm_button_text" to "Set 08:00 Stretch",
                        ),
                        notificationTitle = "Active Recovery Session",
                        notificationBody = "You mentioned feeling sore. Would a gentle 08:00 mobility and stretch session fit better than high intensity today?",
                        confirmButtonText = "Set 08:00 Stretch",
                    )
                    else -> ProposedAction(
                        toolName = "set_alarm",
                        toolParams = mapOf(
                            "hour" to "7",
                            "minute" to "0",
                            "label" to "Gym",
                            "nudge_title" to "Morning Gym Routine",
                            "nudge_body" to "I noticed you missed the gym today. Would a 07:00 alarm help get you there tomorrow?",
                            "confirm_button_text" to "Set 07:00 Alarm",
                        ),
                        notificationTitle = "Morning Gym Routine",
                        notificationBody = "I noticed you missed the gym today. Would a 07:00 alarm help get you there tomorrow?",
                        confirmButtonText = "Set 07:00 Alarm",
                    )
                }
            }
            "sleep", "bedtime" -> {
                ProposedAction(
                    toolName = "set_alarm",
                    toolParams = mapOf(
                        "hour" to "22",
                        "minute" to "15",
                        "label" to "Screen Off",
                        "nudge_title" to "Bedtime Wind-Down",
                        "nudge_body" to "To prevent late night wakefulness, would a 22:15 screen-off alert help you wind down?",
                        "confirm_button_text" to "Set 22:15 Reminder",
                    ),
                    notificationTitle = "Bedtime Wind-Down",
                    notificationBody = "To prevent late night wakefulness, would a 22:15 screen-off alert help you wind down?",
                    confirmButtonText = "Set 22:15 Reminder",
                )
            }
            "health", "wellness" -> {
                ProposedAction(
                    toolName = "set_alarm",
                    toolParams = mapOf(
                        "hour" to "09",
                        "minute" to "00",
                        "label" to "Health Check",
                        "nudge_title" to "Daily Wellness Check",
                        "nudge_body" to "Would a 09:00 reminder help keep your health and hydration routine consistent?",
                        "confirm_button_text" to "Set 09:00 Reminder",
                    ),
                    notificationTitle = "Daily Wellness Check",
                    notificationBody = "Would a 09:00 reminder help keep your health and hydration routine consistent?",
                    confirmButtonText = "Set 09:00 Reminder",
                )
            }
            "work", "productivity" -> {
                ProposedAction(
                    toolName = "set_alarm",
                    toolParams = mapOf(
                        "hour" to "09",
                        "minute" to "30",
                        "label" to "Focus Block",
                        "nudge_title" to "Morning Focus Block",
                        "nudge_body" to "Would a 09:30 alert help you kickstart your deep work priority before meetings?",
                        "confirm_button_text" to "Set 09:30 Focus",
                    ),
                    notificationTitle = "Morning Focus Block",
                    notificationBody = "Would a 09:30 alert help you kickstart your deep work priority before meetings?",
                    confirmButtonText = "Set 09:30 Focus",
                )
            }
            else -> {
                ProposedAction(
                    toolName = "set_alarm",
                    toolParams = mapOf(
                        "hour" to "08",
                        "minute" to "00",
                        "label" to context.threadTitle,
                        "nudge_title" to "${context.threadTitle} Reminder",
                        "nudge_body" to "Would an 08:00 reminder help support your ${context.threadTitle.lowercase()} routine tomorrow?",
                        "confirm_button_text" to "Set 08:00 Reminder",
                    ),
                    notificationTitle = "${context.threadTitle} Reminder",
                    notificationBody = "Would an 08:00 reminder help support your ${context.threadTitle.lowercase()} routine tomorrow?",
                    confirmButtonText = "Set 08:00 Reminder",
                )
            }
        }

        return ReasoningProposal(
            detectedRootCause = detectedRootCause,
            proposedAction = proposedAction,
            reasoningSummary = "Reflected on ${context.threadTopic} with root cause '$detectedRootCause'",
        )
    }
}
