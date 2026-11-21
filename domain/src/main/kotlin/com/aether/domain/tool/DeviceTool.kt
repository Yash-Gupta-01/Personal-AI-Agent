package com.aether.domain.tool

data class ToolExecutionResult(
    val success: Boolean,
    val message: String? = null,
    val outputData: Map<String, String> = emptyMap(),
)

/**
 * Port for device capabilities (alarms, notifications, calendar).
 *
 * The assistant never touches platform APIs directly; every action
 * is declared as a tool conforming to this contract.
 */
interface DeviceTool {
    val name: String
    suspend fun execute(params: Map<String, String>): ToolExecutionResult
}
