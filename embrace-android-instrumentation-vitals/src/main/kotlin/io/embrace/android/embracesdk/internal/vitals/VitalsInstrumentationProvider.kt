package io.embrace.android.embracesdk.internal.vitals

import android.os.Build
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState

/**
 * SPI entry point for the mobile vitals instrumentation. Registers nothing below API 24.
 */
class VitalsInstrumentationProvider : InstrumentationProvider {

    override fun register(args: InstrumentationArgs): DataSourceState<DataSource>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return null
        }
        return {
            if (args.configService.autoDataCaptureBehavior.isSmoothnessCaptureEnabled()) {
                VitalsDataSource(args)
            } else {
                null
            }
        }
    }
}
