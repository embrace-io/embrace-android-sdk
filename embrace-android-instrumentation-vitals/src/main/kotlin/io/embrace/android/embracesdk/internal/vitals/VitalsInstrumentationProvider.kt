package io.embrace.android.embracesdk.internal.vitals

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceFactory

/**
 * SPI entry point for the mobile vitals instrumentation.
 */
class VitalsInstrumentationProvider : InstrumentationProvider {

    override fun register(args: InstrumentationArgs): DataSourceFactory<DataSource>? {
        return {
            if (args.configService.config.autoDataCapture.smoothnessCaptureEnabled) {
                VitalsDataSource(args)
            } else {
                null
            }
        }
    }
}
