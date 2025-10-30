package io.embrace.android.embracesdk.internal.instrumentation.view

import io.embrace.android.embracesdk.internal.arch.InstrumentationInstallArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState

class ViewInstrumentationProvider : InstrumentationProvider {
    override fun register(args: InstrumentationInstallArgs): DataSourceState<*>? {
        return DataSourceState(
            factory = {
                ViewDataSource(
                    args.application,
                    args.configService.breadcrumbBehavior,
                    args.clock,
                    args.telemetryDestination,
                    args.logger
                )
            },
            configGate = { args.configService.breadcrumbBehavior.isActivityBreadcrumbCaptureEnabled() }
        )
    }
}
