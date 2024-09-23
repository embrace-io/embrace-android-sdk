package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.EmbraceFeatureRegistry
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState

class FakeFeatureRegistry : EmbraceFeatureRegistry {

    val states: MutableList<DataSourceState<*>> = mutableListOf()

    override fun add(state: DataSourceState<*>) {
        states.add(state)
    }
}
