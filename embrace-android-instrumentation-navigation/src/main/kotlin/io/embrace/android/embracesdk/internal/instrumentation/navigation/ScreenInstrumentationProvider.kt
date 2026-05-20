package io.embrace.android.embracesdk.internal.instrumentation.navigation

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.StateInstrumentationProvider

class ScreenInstrumentationProvider :
    StateInstrumentationProvider<ScreenDataSource, String>() {

    override fun factoryProvider(args: InstrumentationArgs): () -> ScreenDataSource {
        return { ScreenDataSource(args) }
    }
}
