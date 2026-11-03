package io.embrace.android.embracesdk.internal.instrumentation.crash.jvm

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceFactory

/**
 * Not discovered via SPI like other data sources. The SDK has to register it directly so it can do unique post registration
 * work that is tied to the SDK's lifecycle.
 */
class JvmCrashInstrumentationProvider : InstrumentationProvider {
    override fun register(args: InstrumentationArgs): DataSourceFactory<JvmCrashDataSource> {
        return {
            JvmCrashDataSourceImpl(args)
        }
    }
}
