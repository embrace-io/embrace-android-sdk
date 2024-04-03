package io.embrace.android.embracesdk.capture.thermalstate

import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.payload.ThermalState
import io.embrace.android.embracesdk.worker.BackgroundWorker
import io.embrace.android.embracesdk.worker.TaskPriority
import java.util.LinkedList
import java.util.concurrent.Executor

private const val CAPTURE_LIMIT = 100

@RequiresApi(Build.VERSION_CODES.Q)
internal class EmbraceThermalStatusService(
    private val backgroundWorker: BackgroundWorker,
    private val clock: Clock,
    powerManagerProvider: Provider<PowerManager?>
) : ThermalStatusService {

    private val thermalStates = LinkedList<ThermalState>()

    private val thermalStatusListener = PowerManager.OnThermalStatusChangedListener {
        handleThermalStateChange(it)
    }

    private val powerManager: PowerManager? by lazy(powerManagerProvider)

    init {
        backgroundWorker.submit(TaskPriority.LOW) {
            Systrace.traceSynchronous("thermal-service-registration") {
                val pm = powerManager
                if (pm != null) {
                    // Android API only accepts an executor. We don't want to directly expose those
                    // to everything in the codebase so we decorate the BackgroundWorker here as an
                    // alternative
                    val executor = Executor {
                        backgroundWorker.submit(runnable = it)
                    }
                    pm.addThermalStatusListener(executor, thermalStatusListener)
                }
            }
        }
    }

    fun handleThermalStateChange(status: Int?) {
        if (status == null) {
            return
        }

        thermalStates.add(ThermalState(clock.now(), status))

        if (thermalStates.size > CAPTURE_LIMIT) {
            thermalStates.removeFirst()
        }
    }

    override fun cleanCollections() = thermalStates.clear()

    override fun getCapturedData(): List<ThermalState> = thermalStates

    override fun close() {
        powerManager?.removeThermalStatusListener(thermalStatusListener)
    }
}
