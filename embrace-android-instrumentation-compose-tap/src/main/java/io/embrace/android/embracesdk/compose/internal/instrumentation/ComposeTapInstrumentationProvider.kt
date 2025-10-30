package io.embrace.android.embracesdk.compose.internal.instrumentation

import io.embrace.android.embracesdk.internal.arch.InstrumentationInstallArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.instrumentation.tapDataSource

class ComposeTapInstrumentationProvider : InstrumentationProvider {
    override fun register(args: InstrumentationInstallArgs): DataSourceState<*>? {
        return DataSourceState(
            factory = {
                ComposeTapDataSource(
                    breadcrumbBehavior = args.configService.breadcrumbBehavior,
                    destination = args.telemetryDestination,
                    logger = args.logger,
                    tapDataSourceProvider = { tapDataSource },
                    app = args.application,
                )
            },
            configGate = args.configService.autoDataCaptureBehavior::isComposeClickCaptureEnabled
        )
    }
}
