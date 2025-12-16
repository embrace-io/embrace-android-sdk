package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.StateInstrumentationProvider

class TestInstrumentationProvider : StateInstrumentationProvider<TestStateDataSource, String>() {
    override fun factoryProvider(args: InstrumentationArgs): () -> TestStateDataSource {
        return {
            TestStateDataSource(args)
        }
    }
}

