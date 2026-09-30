package io.embrace.android.embracesdk.internal.instrumentation.thermalstate

import android.os.Build
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceFactory

class ThermalStateInstrumentationProvider : InstrumentationProvider {

    override val asyncInit: Boolean = true

    override fun register(args: InstrumentationArgs): DataSourceFactory<DataSource>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null
        }
        return {
            if (args.configService.config.autoDataCapture.thermalStatusCaptureEnabled) {
                ThermalStateDataSource(args = args)
            } else {
                null
            }
        }
    }
}
