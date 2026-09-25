package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState

class FakeInstrumentationProvider(
    override val priority: Int = 10000,
    private val action: (k: Int) -> Unit,
    private val dataSourceState: DataSourceState<DataSource>? = null,
    override val asyncInit: Boolean = false,
) : InstrumentationProvider {

    override fun register(args: InstrumentationArgs): DataSourceState<DataSource>? {
        action(priority)
        return dataSourceState
    }
}
