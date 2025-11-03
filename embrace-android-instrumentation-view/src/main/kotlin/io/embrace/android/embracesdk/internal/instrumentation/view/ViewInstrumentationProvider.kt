package io.embrace.android.embracesdk.internal.instrumentation.view

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState

class ViewInstrumentationProvider : InstrumentationProvider {
    override fun register(args: InstrumentationArgs): DataSourceState<*>? {
        return DataSourceState(
            factory = {
                ViewDataSource(args)
            },
            configGate = { args.configService.breadcrumbBehavior.isActivityBreadcrumbCaptureEnabled() }
        )
    }
}
