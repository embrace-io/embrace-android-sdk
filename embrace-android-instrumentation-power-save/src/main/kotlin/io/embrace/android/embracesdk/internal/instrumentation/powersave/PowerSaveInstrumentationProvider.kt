package io.embrace.android.embracesdk.internal.instrumentation.powersave

import android.content.Context
import io.embrace.android.embracesdk.internal.arch.InstrumentationInstallArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.worker.Worker

class PowerSaveInstrumentationProvider : InstrumentationProvider {
    override fun register(args: InstrumentationInstallArgs): DataSourceState<*>? {
        return DataSourceState(
            factory = {
                LowPowerDataSource(
                    context = args.context,
                    backgroundWorker = args.backgroundWorker(Worker.Background.NonIoRegWorker),
                    clock = args.clock,
                    provider = { args.systemService(Context.POWER_SERVICE) },
                    traceWriter = args.traceWriter,
                    logger = args.logger,
                )
            },
            configGate = { args.configService.autoDataCaptureBehavior.isPowerSaveModeCaptureEnabled() }
        )
    }
}
