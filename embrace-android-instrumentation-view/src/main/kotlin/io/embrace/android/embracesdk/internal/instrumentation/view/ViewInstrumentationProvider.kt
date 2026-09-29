package io.embrace.android.embracesdk.internal.instrumentation.view

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceFactory

class ViewInstrumentationProvider : InstrumentationProvider {
    override fun register(args: InstrumentationArgs): DataSourceFactory<DataSource>? {
        return {
            if (args.configService.config.breadcrumb.captureActivities) {
                ViewDataSource(args)
            } else {
                null
            }
        }
    }
}
