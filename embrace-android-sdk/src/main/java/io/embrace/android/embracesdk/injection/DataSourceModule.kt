package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.capture.crumbs.CustomBreadcrumbDataSource
import io.embrace.android.embracesdk.capture.crumbs.FragmentBreadcrumbDataSource
import io.embrace.android.embracesdk.worker.WorkerThreadModule
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

    val customBreadcrumbDataSource: DataSourceState
    val fragmentBreadcrumbDataSource: DataSourceState
}

internal class DataSourceModuleImpl(
    essentialServiceModule: EssentialServiceModule,
    initModule: InitModule,
    otelModule: OpenTelemetryModule,
    @Suppress("UNUSED_PARAMETER") systemServiceModule: SystemServiceModule,
    @Suppress("UNUSED_PARAMETER") androidServicesModule: AndroidServicesModule,
    @Suppress("UNUSED_PARAMETER") workerThreadModule: WorkerThreadModule,
) : DataSourceModule {

    private val values: MutableList<DataSourceState> = mutableListOf()

    override val customBreadcrumbDataSource: DataSourceState by dataSource {
        DataSourceState({
            CustomBreadcrumbDataSource(
                configService = essentialServiceModule.configService,
                writer = otelModule.currentSessionSpan
            )
        })
    }

    override val fragmentBreadcrumbDataSource: DataSourceState by dataSource {
        DataSourceState(
            factory = {
                FragmentBreadcrumbDataSource(
                    configService,
                    initModule.clock,
                    otelModule.spanService
                )
            },
            configGate = { configService.breadcrumbBehavior.isActivityBreadcrumbCaptureEnabled() }
        )
    }

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
