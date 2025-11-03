package io.embrace.android.embracesdk.internal.instrumentation.thermalstate

import android.content.Context
import android.os.Build
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.worker.Worker

class ThermalStateInstrumentationProvider : InstrumentationProvider {

    override fun register(args: InstrumentationArgs): DataSourceState<*>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null
        }
        return DataSourceState(
            factory = {
                ThermalStateDataSource( // FIXME: supply only args.
                    args = args,
                    backgroundWorker = args.backgroundWorker(Worker.Background.NonIoRegWorker),
                    powerManagerProvider = { args.systemService(Context.POWER_SERVICE) },
                )
            },
            configGate = {
                args.configService.autoDataCaptureBehavior.isThermalStatusCaptureEnabled()
            }
        )
    }
}
