package io.embrace.android.embracesdk.internal.instrumentation.network

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.StateInstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType

class NetworkStateInstrumentationProvider :
    StateInstrumentationProvider<NetworkStateDataSource, SchemaType.NetworkState.Status>(
        configGate = {
            configService.autoDataCaptureBehavior.isNetworkConnectivityCaptureEnabled()
        }
    ) {
    override fun factoryProvider(args: InstrumentationArgs): () -> NetworkStateDataSource {
        return { NetworkStateDataSource(args) }
    }
}
