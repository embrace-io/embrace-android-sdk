package io.embrace.android.embracesdk.capture.powersave

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logDebug
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logDeveloper
import io.embrace.android.embracesdk.payload.PowerModeInterval
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateListener
import io.embrace.android.embracesdk.worker.BackgroundWorker

internal class EmbracePowerSaveModeService(
    private val context: Context,
    private val backgroundWorker: BackgroundWorker,
    private val clock: Clock,
    private val powerManager: PowerManager?
) : BroadcastReceiver(), PowerSaveModeService, ProcessStateListener {

    private companion object {
        private const val MAX_CAPTURED_POWER_MODE_INTERVALS = 100
    }

    private val tag = "EmbracePowerSaveModeService"

    private val powerSaveIntentFilter = IntentFilter(ACTION_POWER_SAVE_MODE_CHANGED)

    private val powerSaveModeIntervals = mutableListOf<PowerChange>()

    init {
        registerPowerSaveModeReceiver()
    }

    private fun registerPowerSaveModeReceiver() {
        backgroundWorker.submit {
            try {
                context.registerReceiver(this, powerSaveIntentFilter)
                logDeveloper(tag, "registered power save mode changed")
            } catch (ex: Exception) {
                InternalStaticEmbraceLogger.logError(
                    "Failed to register: $tag broadcast receiver. Power save mode status will be unavailable.",
                    ex
                )
            }
        }
    }

    override fun onForeground(coldStart: Boolean, timestamp: Long) {
        if (powerManager?.isPowerSaveMode == true) {
            addPowerChange(PowerChange(timestamp, Kind.START))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        logDeveloper(tag, "onReceive")
        try {
            when (intent.action) {
                ACTION_POWER_SAVE_MODE_CHANGED ->
                    addPowerChange(
                        PowerChange(
                            clock.now(),
                            if (powerManager?.isPowerSaveMode == true) Kind.START else Kind.END
                        )
                    )
            }
        } catch (ex: Exception) {
            InternalStaticEmbraceLogger.logError("Failed to handle " + intent.action, ex)
        }
    }

    private fun addPowerChange(powerChange: PowerChange) {
        if (powerSaveModeIntervals.size < MAX_CAPTURED_POWER_MODE_INTERVALS) {
            powerSaveModeIntervals.add(powerChange)
        }
    }

    override fun getCapturedData(): List<PowerModeInterval> {
        val intervals = mutableListOf<PowerModeInterval>()
        for (powerChange in powerSaveModeIntervals) {
            if (powerChange.time >= 0) {
                when (powerChange.kind) {
                    Kind.START -> {
                        intervals.add(PowerModeInterval(powerChange.time))
                    }

                    Kind.END -> {
                        if (intervals.isNotEmpty() && intervals.last().startTime != 0L) {
                            intervals[intervals.size - 1] =
                                intervals.last().copy(endTime = powerChange.time)
                        } else {
                            intervals.add(PowerModeInterval(0, powerChange.time))
                        }
                    }
                }
            }
        }
        return intervals
    }

    override fun close() {
        logDebug("Stopping $tag")
        context.unregisterReceiver(this)
    }

    override fun cleanCollections() = powerSaveModeIntervals.clear()

    data class PowerChange(val time: Long, val kind: Kind)

    enum class Kind { START, END }
}
