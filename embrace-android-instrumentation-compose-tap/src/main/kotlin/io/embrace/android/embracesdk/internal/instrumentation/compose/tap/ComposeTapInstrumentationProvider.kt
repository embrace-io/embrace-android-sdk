package io.embrace.android.embracesdk.internal.instrumentation.compose.tap

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceFactory
import io.embrace.android.embracesdk.internal.instrumentation.view.taps.tapDataSource

class ComposeTapInstrumentationProvider : InstrumentationProvider {
    override fun register(args: InstrumentationArgs): DataSourceFactory<DataSource>? {
        return {
            if (args.configService.autoDataCaptureBehavior.isComposeClickCaptureEnabled()) {
                ComposeTapDataSource(
                    args = args,
                    tapDataSourceProvider = { tapDataSource },
                )
            } else {
                null
            }
        }
    }
}
