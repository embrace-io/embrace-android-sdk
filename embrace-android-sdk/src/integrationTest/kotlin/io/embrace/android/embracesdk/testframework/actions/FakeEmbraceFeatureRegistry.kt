package io.embrace.android.embracesdk.testframework.actions

import io.embrace.android.embracesdk.internal.arch.EmbraceFeatureRegistry
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState

internal class FakeEmbraceFeatureRegistry(private val impl: EmbraceFeatureRegistry) : EmbraceFeatureRegistry {
    val states: MutableList<DataSourceState<*>> = mutableListOf()

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : DataSource> findByType(): T {
        val state = states.firstOrNull { it.dataSource is T } as? DataSourceState<T>
        return state?.dataSource ?: error("Unable to find data source for ${T::class.simpleName}")
    }

    override fun add(state: DataSourceState<*>) {
        states.add(state)
        impl.add(state)
    }
}
