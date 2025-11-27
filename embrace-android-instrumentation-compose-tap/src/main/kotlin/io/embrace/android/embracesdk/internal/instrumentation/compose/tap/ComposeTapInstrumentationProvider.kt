package io.embrace.android.embracesdk.internal.instrumentation.compose.tap

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.instrumentation.view.taps.tapDataSource

class ComposeTapInstrumentationProvider : InstrumentationProvider {
    override fun register(args: InstrumentationArgs): DataSourceState<*>? {
        return DataSourceState(
            factory = {
                ComposeTapDataSource(
                    args = args,
                    tapDataSourceProvider = { tapDataSource },
                )
            },
            configGate = args.configService.autoDataCaptureBehavior::isComposeClickCaptureEnabled
        )
    }
}
