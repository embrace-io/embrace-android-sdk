package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.EmbraceFeatureRegistry
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import kotlin.reflect.KClass

class FakeFeatureRegistry : EmbraceFeatureRegistry {

    val states: MutableList<DataSourceState<*>> = mutableListOf()

    override fun add(state: DataSourceState<*>) {
        states.add(state)
    }

    override fun <T : DataSource> findByType(clazz: KClass<T>): T? {
        throw UnsupportedOperationException()
    }
}
