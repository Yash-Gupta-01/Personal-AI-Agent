package com.aether.domain.action

/**
 * Technical execution status of a device tool (e.g. SetAlarmTool, Trampoline).
 */
enum class ToolExecutionOutcome {
    NOT_APPLICABLE,
    SCHEDULED,
    EXECUTED,
    FAILED,
    CANCELLED,
}

/**
 * User routine compliance outcome (e.g. did the user actually sleep early as proposed?).
 */
enum class BehavioralOutcome {
    UNKNOWN,
    COMPLIANT,
    NON_COMPLIANT,
}

/**
 * Real-world situational circumstances (e.g. illness or external obstacles).
 */
enum class SituationalOutcome {
    UNCHECKED,
    OBSTACLE_PRESENT,
    OBSTACLE_RESOLVED,
}

/**
 * Decouples tool execution, behavioral adherence, and real-world situations
 * into three independent, non-conflated dimensions.
 */
data class TripartiteState(
    val toolOutcome: ToolExecutionOutcome = ToolExecutionOutcome.NOT_APPLICABLE,
    val behavioralOutcome: BehavioralOutcome = BehavioralOutcome.UNKNOWN,
    val situationalOutcome: SituationalOutcome = SituationalOutcome.UNCHECKED,
)
