package io.embrace.android.embracesdk.instrumentation.leaks

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState

class LeakDetectionInstrumentationProvider : InstrumentationProvider {
    override fun register(args: InstrumentationArgs): DataSourceState<*> {
        return DataSourceState(
            factory = { LeakDetectionDataSource(args) },
            configGate = { true },
        )
    }
}
