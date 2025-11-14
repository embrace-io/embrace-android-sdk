package io.embrace.android.embracesdk.testframework.actions

import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState

internal class FakeInstrumentationRegistry(
    private val impl: InstrumentationRegistry,
) : InstrumentationRegistry by impl {

    val states: MutableList<DataSourceState<*>> = mutableListOf()

    override fun add(state: DataSourceState<*>) {
        states.add(state)
        impl.add(state)
    }
}
