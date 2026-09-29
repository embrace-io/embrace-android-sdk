package io.embrace.android.embracesdk.internal.instrumentation

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceFactory

class HucLiteInstrumentationProvider : InstrumentationProvider {
    override fun register(args: InstrumentationArgs): DataSourceFactory<DataSource>? {
        return {
            if (args.configService.networkBehavior.isHucLiteInstrumentationEnabled()) {
                HucLiteDataSource(
                    args = args,
                )
            } else {
                null
            }
        }
    }
}
