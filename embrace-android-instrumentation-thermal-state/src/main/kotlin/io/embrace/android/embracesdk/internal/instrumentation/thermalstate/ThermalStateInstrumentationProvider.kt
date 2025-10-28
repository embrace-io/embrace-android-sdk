package io.embrace.android.embracesdk.internal.instrumentation.thermalstate

import android.content.Context
import android.os.Build
import io.embrace.android.embracesdk.internal.arch.InstrumentationInstallArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.worker.Worker

class ThermalStateInstrumentationProvider : InstrumentationProvider {

    override fun register(args: InstrumentationInstallArgs): DataSourceState<*>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null
        }
        return DataSourceState(
            factory = {
                ThermalStateDataSource(
                    destination = args.telemetryDestination,
                    logger = args.logger,
                    backgroundWorker = args.backgroundWorker(Worker.Background.NonIoRegWorker),
                    clock = args.clock,
                    powerManagerProvider = { args.systemService(Context.POWER_SERVICE) },
                )
            },
            configGate = {
                args.configService.autoDataCaptureBehavior.isThermalStatusCaptureEnabled()
            }
        )
    }
}
