package io.embrace.android.embracesdk.internal.capture.powersave

import android.content.Context
import android.os.PowerManager
import io.embrace.android.embracesdk.internal.arch.datasource.NoInputValidation
import io.embrace.android.embracesdk.internal.arch.datasource.SpanDataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.startSpanCapture
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.spans.EmbraceSpan

internal class LowPowerDataSource(
    private val context: Context,
    spanService: SpanService,
    logger: EmbLogger,
    private val backgroundWorker: BackgroundWorker,
    private val clock: Clock,
    provider: Provider<PowerManager?>
) : SpanDataSourceImpl(
    destination = spanService,
    logger = logger,
    limitStrategy = UpToLimitStrategy { MAX_CAPTURED_POWER_MODE_INTERVALS }
) {

    private companion object {
        private const val MAX_CAPTURED_POWER_MODE_INTERVALS = 100
    }

    private val receiver = PowerSaveModeReceiver(logger, provider, ::onPowerSaveModeChanged)
    private var span: EmbraceSpan? = null

    override fun enableDataCapture() = receiver.register(context, backgroundWorker)
    override fun disableDataCapture() = receiver.unregister(context)

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
