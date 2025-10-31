package io.embrace.android.embracesdk.internal.instrumentation.thermalstate

import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.SpanToken
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.Executor

@RequiresApi(Build.VERSION_CODES.Q)
class ThermalStateDataSource(
    args: InstrumentationArgs,
    private val backgroundWorker: BackgroundWorker,
    powerManagerProvider: Provider<PowerManager?>,
) : DataSourceImpl(
    args,
    limitStrategy = UpToLimitStrategy { MAX_CAPTURED_THERMAL_STATES }
) {
    private companion object {
        private const val MAX_CAPTURED_THERMAL_STATES = 100
    }

    private var thermalStatusListener: PowerManager.OnThermalStatusChangedListener? = null

    private val powerManager: PowerManager? by lazy(powerManagerProvider)

    private var span: SpanToken? = null

    override fun onDataCaptureEnabled() {
        backgroundWorker.submit {
            EmbTrace.trace("thermal-service-registration") {
                thermalStatusListener = PowerManager.OnThermalStatusChangedListener {
                    handleThermalStateChange(it)
                }
                val pm = powerManager
                if (pm != null) {
                    // Android API only accepts an executor. We don't want to directly expose those
                    // to everything in the codebase so we decorate the BackgroundWorker here as an
                    // alternative
                    val executor = Executor {
                        backgroundWorker.submit(runnable = it)
                    }
                    thermalStatusListener?.let {
                        pm.addThermalStatusListener(executor, it)
                    }
                }
            }
        }
    }

    override fun onDataCaptureDisabled() {
        backgroundWorker.submit {
            thermalStatusListener?.let {
                powerManager?.removeThermalStatusListener(it)
                thermalStatusListener = null
            }
        }
    }

    fun handleThermalStateChange(status: Int?) {
        if (status == null) {
            return
        }

        val timestamp = clock.now()

        // close previous span
        if (span != null) {
            span?.stop(endTimeMs = timestamp)
        }
        // start a new span with the new thermal state
        captureTelemetry {
            span = startSpanCapture(SchemaType.ThermalState(status), timestamp)
        }
    }
}
