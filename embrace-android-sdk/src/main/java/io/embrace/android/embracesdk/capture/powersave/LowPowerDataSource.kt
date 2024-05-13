package io.embrace.android.embracesdk.capture.powersave

import android.content.Context
import android.os.PowerManager
import io.embrace.android.embracesdk.arch.datasource.NoInputValidation
import io.embrace.android.embracesdk.arch.datasource.SpanDataSourceImpl
import io.embrace.android.embracesdk.arch.datasource.startSpanCapture
import io.embrace.android.embracesdk.arch.destination.StartSpanData
import io.embrace.android.embracesdk.arch.destination.StartSpanMapper
import io.embrace.android.embracesdk.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.worker.BackgroundWorker

internal class LowPowerDataSource(
    private val context: Context,
    spanService: SpanService,
    logger: EmbLogger,
    private val backgroundWorker: BackgroundWorker,
    private val clock: Clock,
    provider: Provider<PowerManager?>
) : StartSpanMapper<Long>, SpanDataSourceImpl(
    destination = spanService,
    logger = logger,
    limitStrategy = UpToLimitStrategy(logger) { MAX_CAPTURED_POWER_MODE_INTERVALS }
) {

    private companion object {
        private const val MAX_CAPTURED_POWER_MODE_INTERVALS = 100
    }

    private val receiver = PowerSaveModeReceiver(logger, provider, ::onPowerSaveModeChanged)
    private var span: EmbraceSpan? = null

    override fun enableDataCapture() = receiver.register(context, backgroundWorker)
    override fun disableDataCapture() = receiver.unregister(context)
    override fun toStartSpanData(obj: Long) = StartSpanData(SchemaType.LowPower, obj)

    fun onPowerSaveModeChanged(powerSaveMode: Boolean) {
        val activeSpan = span

        if (powerSaveMode && activeSpan == null) {
            alterSessionSpan(NoInputValidation) {
                startSpanCapture(clock.now(), ::toStartSpanData)?.apply {
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
