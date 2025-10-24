package io.embrace.android.embracesdk.internal.capture.powersave

import android.content.Context
import android.os.PowerManager
import io.embrace.android.embracesdk.internal.arch.datasource.NoInputValidation
import io.embrace.android.embracesdk.internal.arch.datasource.SpanDataSourceImpl
import io.embrace.android.embracesdk.internal.arch.destination.SpanToken
import io.embrace.android.embracesdk.internal.arch.destination.TraceWriter
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker

class LowPowerDataSource(
    private val context: Context,
    traceWriter: TraceWriter,
    logger: EmbLogger,
    private val backgroundWorker: BackgroundWorker,
    private val clock: Clock,
    provider: Provider<PowerManager?>,
) : SpanDataSourceImpl(
    destination = traceWriter,
    logger = logger,
    limitStrategy = UpToLimitStrategy { MAX_CAPTURED_POWER_MODE_INTERVALS }
) {

    private companion object {
        private const val MAX_CAPTURED_POWER_MODE_INTERVALS = 100
    }

    private val receiver = PowerSaveModeReceiver(provider, ::onPowerSaveModeChanged)
    private var span: SpanToken? = null

    override fun enableDataCapture(): Unit = receiver.register(context, backgroundWorker)
    override fun disableDataCapture(): Unit = receiver.unregister(context)

    fun onPowerSaveModeChanged(powerSaveMode: Boolean) {
        val activeSpan = span

        if (powerSaveMode && activeSpan == null) {
            captureData(NoInputValidation) {
                startSpanCapture(SchemaType.LowPower, clock.now())?.apply {
                    span = this
                }
            }
        } else if (!powerSaveMode && activeSpan != null) {
            // stops the span
            captureSpanData(
                countsTowardsLimits = false,
                inputValidation = NoInputValidation,
                captureAction = {
                    activeSpan.stop()
                    span = null
                }
            )
        }
    }
}
