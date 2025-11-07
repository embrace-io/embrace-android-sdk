package io.embrace.android.embracesdk.internal.instrumentation.network

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState

private var networkRequestDataSource: NetworkRequestDataSource? = null

fun retrieveNetworkRequestDataSource(): NetworkRequestDataSource? {
    return networkRequestDataSource
}

class NetworkRequestInstrumentationProvider : InstrumentationProvider {
    override fun register(args: InstrumentationArgs): DataSourceState<*>? {
        return DataSourceState(
            factory = {
                networkRequestDataSource = NetworkRequestDataSourceImpl(args)
                networkRequestDataSource
            }
        )
    }

    // higher priority as other network instrumentation can rely on this being initialized first
    override val priority: Int = 5000
}
