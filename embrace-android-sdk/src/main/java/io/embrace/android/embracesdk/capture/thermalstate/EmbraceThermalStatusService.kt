package io.embrace.android.embracesdk.capture.thermalstate

import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.ThermalState
import java.util.LinkedList
import java.util.concurrent.Executor

private const val CAPTURE_LIMIT = 100

@RequiresApi(Build.VERSION_CODES.Q)
internal class EmbraceThermalStatusService(
    executor: Executor,
    private val clock: Clock,
    private val logger: InternalEmbraceLogger,
    private val pm: PowerManager?
) : ThermalStatusService {

    private val thermalStates = LinkedList<ThermalState>()

    private val thermalStatusListener = PowerManager.OnThermalStatusChangedListener {
        handleThermalStateChange(it)
    }

    init {
        pm?.let {
            logger.logDeveloper("ThermalStatusService", "Adding thermal status listener")
            it.addThermalStatusListener(executor, thermalStatusListener)
        }
    }

    fun handleThermalStateChange(status: Int?) {
        if (status == null) {
            logger.logDeveloper("ThermalStatusService", "Null thermal status, no-oping.")
            return
        }

        logger.logDeveloper("ThermalStatusService", "Thermal status change: $status")
        thermalStates.add(ThermalState(clock.now(), status))

        if (thermalStates.size > CAPTURE_LIMIT) {
            logger.logDeveloper(
                "ThermalStatusService",
                "Exceeded capture limit, removing oldest thermal status sample."
            )
            thermalStates.removeFirst()
        }
    }

    override fun cleanCollections() = thermalStates.clear()

    override fun getCapturedData(): List<ThermalState> = thermalStates

    override fun close() {
        pm?.let {
            logger.logDeveloper("ThermalStatusService", "Removing thermal status listener")
            it.removeThermalStatusListener(thermalStatusListener)
        }
    }
}
