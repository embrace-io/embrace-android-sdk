package io.embrace.android.embracesdk.testframework.actions

import io.embrace.android.embracesdk.internal.arch.EmbraceFeatureRegistry
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import kotlin.reflect.KClass

internal class FakeEmbraceFeatureRegistry(private val impl: EmbraceFeatureRegistry) : EmbraceFeatureRegistry {
    val states: MutableList<DataSourceState<*>> = mutableListOf()

    override fun add(state: DataSourceState<*>) {
        states.add(state)
        impl.add(state)
    }

    override fun <T : DataSource> findByType(clazz: KClass<T>): T? = impl.findByType(clazz)
}
