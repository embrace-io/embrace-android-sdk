package io.embrace.android.embracesdk.internal.instrumentation.thermalstate

import android.os.Build
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState

class ThermalStateInstrumentationProvider : InstrumentationProvider {

    override val asyncInit: Boolean = true

    override fun register(args: InstrumentationArgs): DataSourceState<DataSource>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null
        }
        return {
            if (args.configService.autoDataCaptureBehavior.isThermalStatusCaptureEnabled()) {
                ThermalStateDataSource(args = args)
            } else {
                null
            }
        }
    }
}
