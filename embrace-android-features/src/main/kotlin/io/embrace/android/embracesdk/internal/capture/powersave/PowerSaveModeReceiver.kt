package io.embrace.android.embracesdk.internal.capture.powersave

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker

internal class PowerSaveModeReceiver(
    private val powerManagerProvider: Provider<PowerManager?>,
    private val callback: (powerSaveMode: Boolean) -> Unit,
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        runCatching {
            if (intent.action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) {
                powerManagerProvider()?.isPowerSaveMode?.let(callback)
            }
        }
    }

    fun register(context: Context, backgroundWorker: BackgroundWorker) {
        backgroundWorker.submit {
            Systrace.traceSynchronous("power-service-registration") {
                runCatching {
                    if (powerManagerProvider() != null) {
                        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                        context.registerReceiver(this, filter)
                    }
                }
            }
        }
    }

    fun unregister(ctx: Context) {
        ctx.unregisterReceiver(this)
    }
}
