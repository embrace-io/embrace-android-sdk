package io.embrace.android.embracesdk.internal.instrumentation.thermalstate

import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import java.util.concurrent.Executor

@RequiresApi(Build.VERSION_CODES.Q)
internal class PowerManagerThermalStatusNotifier(private val powerManager: PowerManager) : ThermalStatusNotifier {
    override fun addThermalStatusListener(executor: Executor, listener: PowerManager.OnThermalStatusChangedListener) =
        powerManager.addThermalStatusListener(executor, listener)

    override fun removeThermalStatusListener(listener: PowerManager.OnThermalStatusChangedListener) =
        powerManager.removeThermalStatusListener(listener)
}
