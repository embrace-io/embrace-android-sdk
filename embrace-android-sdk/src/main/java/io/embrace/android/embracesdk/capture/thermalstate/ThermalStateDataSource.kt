package io.embrace.android.embracesdk.capture.thermalstate

import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import io.embrace.android.embracesdk.arch.datasource.NoInputValidation
import io.embrace.android.embracesdk.arch.datasource.SpanDataSourceImpl
import io.embrace.android.embracesdk.arch.datasource.startSpanCapture
import io.embrace.android.embracesdk.arch.destination.StartSpanData
import io.embrace.android.embracesdk.arch.destination.StartSpanMapper
import io.embrace.android.embracesdk.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.worker.BackgroundWorker
import io.embrace.android.embracesdk.worker.TaskPriority
import java.util.concurrent.Executor

@RequiresApi(Build.VERSION_CODES.Q)
internal class ThermalStateDataSource(
    spanService: SpanService,
    logger: InternalEmbraceLogger,
    private val backgroundWorker: BackgroundWorker,
    private val clock: Clock,
    powerManagerProvider: Provider<PowerManager?>
) : StartSpanMapper<ThermalState>, SpanDataSourceImpl(
    destination = spanService,
    logger = logger,
    limitStrategy = UpToLimitStrategy(logger) { MAX_CAPTURED_THERMAL_STATES }
) {
    private companion object {
        private const val MAX_CAPTURED_THERMAL_STATES = 100
    }

    private val thermalStatusListener = PowerManager.OnThermalStatusChangedListener {
        handleThermalStateChange(it)
    }

    private val powerManager: PowerManager? by lazy(powerManagerProvider)

    private var span: EmbraceSpan? = null

    override fun enableDataCapture() {
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
            startSpanCapture(ThermalState(status, timestamp), ::toStartSpanData)
                .apply {
                    span = this
                }
        }
    }

    override fun toStartSpanData(obj: ThermalState): StartSpanData {
        return StartSpanData(
            schemaType = SchemaType.ThermalState(obj.status),
            spanStartTimeMs = obj.timestamp
        )
    }
}

internal data class ThermalState(
    val status: Int,
    val timestamp: Long
)
