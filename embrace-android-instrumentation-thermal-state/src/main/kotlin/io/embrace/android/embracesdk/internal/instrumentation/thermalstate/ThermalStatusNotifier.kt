package io.embrace.android.embracesdk.internal.instrumentation.thermalstate

import android.os.PowerManager
import java.util.concurrent.Executor

/**
 * Registers thermal status listeners. [PowerManager] is final, so this lets tests fake it.
 */
internal interface ThermalStatusNotifier {
    fun addThermalStatusListener(executor: Executor, listener: PowerManager.OnThermalStatusChangedListener)
    fun removeThermalStatusListener(listener: PowerManager.OnThermalStatusChangedListener)
}
