package io.embrace.android.embracesdk.internal.instrumentation.powersave

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker

internal class PowerSaveModeReceiver(
    private val powerSaveModeSourceProvider: Provider<PowerSaveModeSource?>,
    private val callback: (powerSaveMode: Boolean) -> Unit,
) : BroadcastReceiver() {

    // avoid synthetic accessors
    val lock = Any()
    var registered = false
    var closed = false

    override fun onReceive(context: Context, intent: Intent) {
        runCatching {
            if (intent.action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) {
                powerSaveModeSourceProvider()?.isPowerSaveMode()?.let(callback)
            }
        }
    }

    fun register(context: Context, backgroundWorker: BackgroundWorker) {
        backgroundWorker.submit {
            EmbTrace.trace("power-service-registration") {
                runCatching {
                    if (powerSaveModeSourceProvider() != null) {
                        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                        synchronized(lock) {
                            if (!closed) {
                                context.registerReceiver(this, filter)
                                registered = true
                            }
                        }
                    }
                }
            }
        }
    }

    fun unregister(context: Context) {
        synchronized(lock) {
            closed = true
            if (registered) {
                registered = false
                runCatching {
                    context.unregisterReceiver(this)
                }
            }
        }
    }
}
