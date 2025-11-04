package io.embrace.android.embracesdk.internal.instrumentation

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState

class HucLiteInstrumentationProvider : InstrumentationProvider {
    override fun register(args: InstrumentationArgs): DataSourceState<*> {
        return DataSourceState(
            factory = {
                HucLiteDataSource(
                    args = args
                )
            },
            configGate = args.configService.networkBehavior::isHucLiteInstrumentationEnabled
        )
    }
}
