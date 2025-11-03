package io.embrace.android.embracesdk.internal.instrumentation.crash.jvm

import android.content.Context
import android.os.PowerManager
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.worker.Worker

class JvmCrashInstrumentationProvider : InstrumentationProvider {
    override fun register(args: InstrumentationArgs): DataSourceState<*>? {
        return DataSourceState(
            factory = {
                LowPowerDataSource(
                    context = args.context,
                    backgroundWorker = args.backgroundWorker(Worker.Background.NonIoRegWorker),
                    clock = args.clock,
                    provider = { args.systemService<PowerManager>(Context.POWER_SERVICE) },
                    destination = args.destination,
                    logger = args.logger,
                )
            },
            configGate = { args.configService.autoDataCaptureBehavior.isPowerSaveModeCaptureEnabled() }
        )
    }
}
