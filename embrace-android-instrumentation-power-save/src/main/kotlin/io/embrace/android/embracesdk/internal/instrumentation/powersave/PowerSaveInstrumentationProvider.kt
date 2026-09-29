package io.embrace.android.embracesdk.internal.instrumentation.powersave

import android.content.Context
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceFactory
import io.embrace.android.embracesdk.internal.worker.Worker

class PowerSaveInstrumentationProvider : InstrumentationProvider {

    override val asyncInit: Boolean = true

    override fun register(args: InstrumentationArgs): DataSourceFactory<DataSource>? {
        return {
            if (args.configService.autoDataCaptureBehavior.isPowerSaveModeCaptureEnabled()) {
                LowPowerDataSource( // FIXME: supply via args
                    args = args,
                    backgroundWorker = args.backgroundWorker(Worker.Background.NonIoRegWorker),
                    provider = { args.systemService(Context.POWER_SERVICE) },
                )
            } else {
                null
            }
        }
    }
}
