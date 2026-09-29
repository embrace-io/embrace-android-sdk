package io.embrace.android.embracesdk.internal.instrumentation.crash.jvm

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceFactory

class JvmCrashInstrumentationProvider : InstrumentationProvider {
    override fun register(args: InstrumentationArgs): DataSourceFactory<DataSource>? {
        return {
            JvmCrashDataSourceImpl(args)
        }
    }

    // crashes are important and should be initialized before other instrumentation
    override val priority: Int = 500
}
