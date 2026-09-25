package io.embrace.android.embracesdk.internal.instrumentation.view

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState

class ViewInstrumentationProvider : InstrumentationProvider {
    override fun register(args: InstrumentationArgs): DataSourceState<DataSource>? {
        return {
            if (args.configService.breadcrumbBehavior.isActivityBreadcrumbCaptureEnabled()) {
                ViewDataSource(args)
            } else {
                null
            }
        }
    }
}
