package io.embrace.android.embracesdk.internal.instrumentation.thermalstate

import android.content.Context
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
import io.embrace.android.embracesdk.internal.worker.Worker
import java.util.concurrent.Executor

@RequiresApi(Build.VERSION_CODES.Q)
class ThermalStateDataSource internal constructor(
    args: InstrumentationArgs,
    private val notifierProvider: Provider<ThermalStatusNotifier?> = {
        args.systemService<PowerManager>(Context.POWER_SERVICE)?.let(::PowerManagerThermalStatusNotifier)
    },
) : DataSourceImpl(
    args,
    limitStrategy = UpToLimitStrategy { MAX_CAPTURED_THERMAL_STATES },
    instrumentationName = "thermal_state_data_source",
) {
    private companion object {
        private const val MAX_CAPTURED_THERMAL_STATES = 100
    }

    private val backgroundWorker = args.backgroundWorker(Worker.Background.NonIoRegWorker)

    private var span: SpanToken? = null

    @Volatile
    private var closed = false

    private var registration: Pair<ThermalStatusNotifier, PowerManager.OnThermalStatusChangedListener>? = null

    override fun onDataCaptureEnabled() {
        backgroundWorker.submit {
            EmbTrace.trace("thermal-service-registration") {
                val notifier = notifierProvider() ?: return@trace
                val listener = PowerManager.OnThermalStatusChangedListener {
                    handleThermalStateChange(it)
                }

                // Android API only accepts an executor. We don't want to directly expose those
                // to everything in the codebase so we decorate the BackgroundWorker here as an
                // alternative
                val executor = Executor {
                    backgroundWorker.submit(runnable = it)
                }
                if (!closed && registration == null) {
                    runCatching {
                        notifier.addThermalStatusListener(executor, listener)
                        registration = Pair(notifier, listener)
                    }
                }
            }
        }
    }

    override fun onDataCaptureDisabled() {
        closed = true
        backgroundWorker.submit {
            registration?.let { (notifier, listener) ->
                registration = null
                runCatching {
                    notifier.removeThermalStatusListener(listener)
                }
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
