package io.embrace.android.embracesdk.internal.instrumentation.thermalstate

import android.os.PowerManager
import java.util.concurrent.Executor

internal class FakeThermalStatusNotifier : ThermalStatusNotifier {

    val addedListeners = mutableListOf<PowerManager.OnThermalStatusChangedListener>()
    val removedListeners = mutableListOf<PowerManager.OnThermalStatusChangedListener>()

    override fun addThermalStatusListener(executor: Executor, listener: PowerManager.OnThermalStatusChangedListener) {
        addedListeners.add(listener)
    }

    override fun removeThermalStatusListener(listener: PowerManager.OnThermalStatusChangedListener) {
        removedListeners.add(listener)
    }
}
