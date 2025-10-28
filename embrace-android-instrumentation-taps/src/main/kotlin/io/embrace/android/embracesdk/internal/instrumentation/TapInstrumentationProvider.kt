package io.embrace.android.embracesdk.internal.instrumentation

import io.embrace.android.embracesdk.internal.arch.InstrumentationInstallArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState

// retain a reference for use in bytecode instrumentation
var tapDataSource: TapDataSource? = null

class TapInstrumentationProvider : InstrumentationProvider {
    override fun register(args: InstrumentationInstallArgs): DataSourceState<*>? {
        return DataSourceState(
            factory = {
                tapDataSource = TapDataSource(
                    breadcrumbBehavior = args.configService.breadcrumbBehavior,
                    destination = args.telemetryDestination,
                    logger = args.logger,
                    clock = args.clock,
                )
                tapDataSource
            }
        )
    }
}
