package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.arch.datasource.DataSourceState
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Declares all the data sources that are used by the Embrace SDK.
 *
 * To add a new data source, simply define a new property of type [DataSourceState] using
 * the [dataSource] property delegate. It is important that you use this delegate as otherwise
 * the property won't be propagated to the [DataCaptureOrchestrator].
 *
 * Data will then automatically be captured by the SDK.
 */
internal interface DataSourceModule {

    /**
     * Returns a list of all the data sources that are defined in this module.
     */
    fun getDataSources(): List<DataSourceState>
}

internal class DataSourceModuleImpl(
    essentialServiceModule: EssentialServiceModule,
) : DataSourceModule {
    private val values: MutableList<DataSourceState> = mutableListOf()

    /* Implementation details */

    private val configService = essentialServiceModule.configService
    override fun getDataSources(): List<DataSourceState> = values

    /**
     * Property delegate that adds the value to a
     * list on its creation. That list is then used by the [DataCaptureOrchestrator] to control
     * the data sources.
     */
    @Suppress("unused")
    private fun dataSource(provider: () -> DataSourceState) = DataSourceDelegate(provider, values)
}

private class DataSourceDelegate(
    provider: () -> DataSourceState,
    values: MutableList<DataSourceState>,
) : ReadOnlyProperty<Any?, DataSourceState> {

    private val value = provider()

    init {
        values.add(value)
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>) = value
}
