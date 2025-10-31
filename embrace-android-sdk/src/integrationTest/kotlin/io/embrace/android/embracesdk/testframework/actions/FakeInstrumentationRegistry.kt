package io.embrace.android.embracesdk.testframework.actions

import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.arch.SessionType
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import kotlin.reflect.KClass

internal class FakeInstrumentationRegistry(private val impl: InstrumentationRegistry) : InstrumentationRegistry {
    val states: MutableList<DataSourceState<*>> = mutableListOf()

    override fun add(state: DataSourceState<*>) {
        states.add(state)
        impl.add(state)
    }

    override var currentSessionType: SessionType?
        get() = impl.currentSessionType
        set(value) {
            impl.currentSessionType = value
        }

    override fun <T : DataSource> findByType(clazz: KClass<T>): T? = impl.findByType(clazz)
}
