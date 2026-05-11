package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.StateInstrumentationProvider

class LazyInitInstrumentationProvider : StateInstrumentationProvider<LazyInitDataSource, String>() {
    override fun factoryProvider(args: InstrumentationArgs): () -> LazyInitDataSource {
        return {
            LazyInitDataSource(args)
        }
    }
}

