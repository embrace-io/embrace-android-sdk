package io.embrace.android.embracesdk.capture.powersave

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.worker.BackgroundWorker

internal class PowerSaveModeReceiver(
    private val logger: EmbLogger,
    private val powerManagerProvider: Provider<PowerManager?>,
    private val callback: (powerSaveMode: Boolean) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) {
                powerManagerProvider()?.isPowerSaveMode?.let(callback)
            }
        } catch (ex: Exception) {
            logger.logError("Failed to handle " + intent.action, ex)
        }
    }

    fun register(context: Context, backgroundWorker: BackgroundWorker) {
        backgroundWorker.submit {
            Systrace.traceSynchronous("power-service-registration") {
                try {
                    if (powerManagerProvider() != null) {
                        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                        context.registerReceiver(this, filter)
                    }
                } catch (ex: Exception) {
                    logger.logError(
                        "Failed to register broadcast receiver. Power save mode status will be unavailable.",
                        ex
                    )
                }
            }
        }
    }

    fun unregister(ctx: Context) {
        ctx.unregisterReceiver(this)
    }
}
