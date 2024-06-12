package io.embrace.android.embracesdk.capture.thermalstate

import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import io.embrace.android.embracesdk.arch.datasource.NoInputValidation
import io.embrace.android.embracesdk.arch.datasource.SpanDataSourceImpl
import io.embrace.android.embracesdk.arch.datasource.startSpanCapture
import io.embrace.android.embracesdk.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.worker.BackgroundWorker
import io.embrace.android.embracesdk.worker.TaskPriority
import java.util.concurrent.Executor

@RequiresApi(Build.VERSION_CODES.Q)
internal class ThermalStateDataSource(
    spanService: SpanService,
    logger: EmbLogger,
    private val backgroundWorker: BackgroundWorker,
    private val clock: Clock,
    powerManagerProvider: Provider<PowerManager?>
) : SpanDataSourceImpl(
    destination = spanService,
    logger = logger,
    limitStrategy = UpToLimitStrategy { MAX_CAPTURED_THERMAL_STATES }
) {
    private companion object {
        private const val MAX_CAPTURED_THERMAL_STATES = 100
    }

    private var thermalStatusListener: PowerManager.OnThermalStatusChangedListener? = null

    private val powerManager: PowerManager? by lazy(powerManagerProvider)

    private var span: EmbraceSpan? = null

    override fun enableDataCapture() {
        backgroundWorker.submit(TaskPriority.LOW) {
            Systrace.traceSynchronous("thermal-service-registration") {
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

    override fun disableDataCapture() {
        backgroundWorker.submit(TaskPriority.LOW) {
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
            captureSpanData(
                countsTowardsLimits = false,
                inputValidation = NoInputValidation,
                captureAction = {
                    span?.stop(endTimeMs = timestamp)
                }
            )
        }
        // start a new span with the new thermal state
        captureSpanData(
            countsTowardsLimits = true,
            inputValidation = NoInputValidation
        ) {
            startSpanCapture(SchemaType.ThermalState(status), timestamp)
                .apply {
                    span = this
                }
        }
    }
}
