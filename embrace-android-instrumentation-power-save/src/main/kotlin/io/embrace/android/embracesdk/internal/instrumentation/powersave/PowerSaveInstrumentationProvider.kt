package io.embrace.android.embracesdk.internal.instrumentation.powersave

import android.content.Context
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.worker.Worker

class PowerSaveInstrumentationProvider : InstrumentationProvider {
    override fun register(args: InstrumentationArgs): DataSourceState<*>? {
        return DataSourceState(
            factory = {
                LowPowerDataSource( // FIXME: supply via args
                    args = args,
                    backgroundWorker = args.backgroundWorker(Worker.Background.NonIoRegWorker),
                    provider = { args.systemService(Context.POWER_SERVICE) },
                )
            },
            configGate = { args.configService.autoDataCaptureBehavior.isPowerSaveModeCaptureEnabled() }
        )
    }
}
