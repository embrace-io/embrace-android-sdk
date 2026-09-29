package io.embrace.android.embracesdk.internal.instrumentation.network

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceFactory

class NetworkStatusInstrumentationProvider : InstrumentationProvider {
    override fun register(args: InstrumentationArgs): DataSourceFactory<DataSource>? {
        return {
            if (args.configService.autoDataCaptureBehavior.isNetworkConnectivityCaptureEnabled()) {
                NetworkStatusDataSource(args)
            } else {
                null
            }
        }
    }
}
