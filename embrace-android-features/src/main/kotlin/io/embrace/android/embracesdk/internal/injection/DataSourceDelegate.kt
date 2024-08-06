package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.arch.EmbraceFeatureRegistry
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.utils.Provider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

internal class DataSourceDelegate<S : DataSource<*>>(
    provider: Provider<DataSourceState<S>>,
    featureRegistry: EmbraceFeatureRegistry,
) : ReadOnlyProperty<Any?, DataSourceState<S>> {

    private val value: DataSourceState<S> = provider()

    init {
        featureRegistry.add(value)
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>) = value
}
