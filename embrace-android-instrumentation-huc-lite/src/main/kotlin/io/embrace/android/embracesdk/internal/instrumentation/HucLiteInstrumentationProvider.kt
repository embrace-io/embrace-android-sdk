package io.embrace.android.embracesdk.internal.instrumentation

import io.embrace.android.embracesdk.internal.arch.InstrumentationInstallArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState

class HucLiteInstrumentationProvider : InstrumentationProvider {
    override fun register(args: InstrumentationInstallArgs): DataSourceState<*> {
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
