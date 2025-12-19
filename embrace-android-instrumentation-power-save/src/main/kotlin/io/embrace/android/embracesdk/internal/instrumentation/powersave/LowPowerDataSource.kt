package io.embrace.android.embracesdk.internal.instrumentation.powersave

import android.os.PowerManager
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.SpanToken
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker

class LowPowerDataSource(
    private val args: InstrumentationArgs,
    private val backgroundWorker: BackgroundWorker,
    provider: Provider<PowerManager?>,
) : DataSourceImpl(
    args = args,
    limitStrategy = UpToLimitStrategy { MAX_CAPTURED_POWER_MODE_INTERVALS },
    instrumentationName = "low_power_data_source"
) {

    private companion object {
        private const val MAX_CAPTURED_POWER_MODE_INTERVALS = 100
    }

    private val receiver = PowerSaveModeReceiver(provider, ::onPowerSaveModeChanged)
    private var span: SpanToken? = null

    override fun onDataCaptureEnabled() = receiver.register(args.context, backgroundWorker)
    override fun onDataCaptureDisabled(): Unit = receiver.unregister(args.context)

    fun onPowerSaveModeChanged(powerSaveMode: Boolean) {
        val activeSpan = span

        if (powerSaveMode && activeSpan == null) {
            captureTelemetry {
                startSpanCapture(SchemaType.LowPower, clock.now())?.apply {
                    span = this
                }
            }
        } else if (!powerSaveMode && activeSpan != null) {
            // stops the span
            activeSpan.stop()
            span = null
        }
    }
}
