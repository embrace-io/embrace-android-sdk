package io.embrace.android.embracesdk.internal.instrumentation.network

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState

class NetworkStatusInstrumentationProvider : InstrumentationProvider {
    override fun register(args: InstrumentationArgs): DataSourceState<*>? {
        return DataSourceState(
            factory = {
                NetworkStatusDataSource(args)
            },
            configGate = {
                args.configService.autoDataCaptureBehavior.isNetworkConnectivityCaptureEnabled()
            }
        )
    }
}
