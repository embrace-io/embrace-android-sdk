package io.embrace.android.embracesdk.internal.instrumentation.thermalstate

import android.os.Build
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState

class ThermalStateInstrumentationProvider : InstrumentationProvider {

    override fun register(args: InstrumentationArgs): DataSourceState<*>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null
        }
        return DataSourceState(
            factory = {
                ThermalStateDataSource(args = args)
            },
            configGate = {
                args.configService.autoDataCaptureBehavior.isThermalStatusCaptureEnabled()
            }
        )
    }
}
