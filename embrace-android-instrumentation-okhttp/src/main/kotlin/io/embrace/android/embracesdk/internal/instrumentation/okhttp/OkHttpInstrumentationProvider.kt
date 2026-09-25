package io.embrace.android.embracesdk.internal.instrumentation.okhttp

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.instrumentation.network.retrieveNetworkCaptureDataSource
import io.embrace.android.embracesdk.internal.instrumentation.network.retrieveNetworkRequestDataSource

// retain a reference for use in bytecode instrumentation
internal var okhttpDataSource: OkHttpDataSource? = null

class OkHttpInstrumentationProvider : InstrumentationProvider {
    override fun register(args: InstrumentationArgs): DataSourceState<DataSource>? {
        return {
            okhttpDataSource = OkHttpDataSource(
                args,
                ::retrieveNetworkRequestDataSource,
                ::retrieveNetworkCaptureDataSource,
            )
            okhttpDataSource
        }
    }
}
