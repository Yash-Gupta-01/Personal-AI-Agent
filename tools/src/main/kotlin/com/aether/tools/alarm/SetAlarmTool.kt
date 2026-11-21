package com.aether.tools.alarm

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.aether.domain.tool.DeviceTool
import com.aether.domain.tool.ToolExecutionResult

/**
 * OEM-Safe tool to program alarms via the device's default Clock app.
 *
 * Uses [AlarmClock.ACTION_SET_ALARM], which is supported across AOSP and OEM
 * skins (ColorOS, HyperOS, OriginOS, One UI).
 *
 * Checks resolveActivity() to ensure a Clock app exists, and passes
 * EXTRA_SKIP_UI = true. If the OEM clock ignores EXTRA_SKIP_UI and presents
 * a pre-filled UI, it is still treated as successful.
 */
class SetAlarmTool(
    private val context: Context,
) : DeviceTool {

    override val name: String = "set_alarm"

    override suspend fun execute(params: Map<String, String>): ToolExecutionResult {
        val hour = params["hour"]?.toIntOrNull()
            ?: return ToolExecutionResult(success = false, message = "Missing or invalid 'hour' parameter")
        val minute = params["minute"]?.toIntOrNull()
            ?: return ToolExecutionResult(success = false, message = "Missing or invalid 'minute' parameter")
        val label = params["label"] ?: "Aether Routine"

        val intent = buildIntent(hour, minute, label)

        val packageManager = context.packageManager
        if (intent.resolveActivity(packageManager) == null) {
            return ToolExecutionResult(
                success = false,
                message = "No Clock application found on device to handle ACTION_SET_ALARM",
            )
        }

        return try {
            context.startActivity(intent)
            ToolExecutionResult(
                success = true,
                message = "Alarm set for %02d:%02d (%s)".format(hour, minute, label),
                outputData = mapOf("hour" to hour.toString(), "minute" to minute.toString(), "label" to label),
            )
        } catch (e: Exception) {
            ToolExecutionResult(success = false, message = "Failed to launch Clock alarm: ${e.message}")
        }
    }

    companion object {
        fun buildIntent(hour: Int, minute: Int, label: String): Intent =
            Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}
