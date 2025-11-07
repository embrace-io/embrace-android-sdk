package io.embrace.android.embracesdk.internal.instrumentation.network

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState

private var networkCaptureDataSource: NetworkCaptureDataSource? = null

fun retrieveNetworkCaptureDataSource(): NetworkCaptureDataSource? {
    return networkCaptureDataSource
}

class NetworkCaptureInstrumentationProvider : InstrumentationProvider {
    override fun register(args: InstrumentationArgs): DataSourceState<*>? {
        return DataSourceState(
            factory = {
                networkCaptureDataSource = NetworkCaptureDataSourceImpl(args)
                networkCaptureDataSource
            }
        )
    }

    // higher priority as other network instrumentation can rely on this being initialized first
    override val priority: Int = 5000
}
