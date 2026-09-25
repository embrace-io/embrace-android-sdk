package io.embrace.android.embracesdk.internal.instrumentation.view.taps

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceFactory

// retain a reference for use in bytecode instrumentation
var tapDataSource: TapDataSource? = null

class TapInstrumentationProvider : InstrumentationProvider {

    override val asyncInit: Boolean = true

    override fun register(args: InstrumentationArgs): DataSourceFactory<DataSource>? {
        return {
            tapDataSource = TapDataSource(args)
            tapDataSource
        }
    }
}
