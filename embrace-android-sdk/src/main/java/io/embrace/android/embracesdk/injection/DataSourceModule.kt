package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.arch.datasource.DataSource
import io.embrace.android.embracesdk.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.capture.crumbs.BreadcrumbDataSource
import io.embrace.android.embracesdk.capture.crumbs.TapDataSource
import io.embrace.android.embracesdk.capture.crumbs.PushNotificationDataSource
import io.embrace.android.embracesdk.capture.crumbs.WebViewUrlDataSource
import io.embrace.android.embracesdk.capture.crumbs.ViewDataSource
import io.embrace.android.embracesdk.capture.session.SessionPropertiesDataSource
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.worker.WorkerThreadModule
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Declares all the data sources that are used by the Embrace SDK.
 *
 * To add a new data source, simply define a new property of type [DataSourceState] using
 * the [dataSourceState] property delegate. It is important that you use this delegate as otherwise
 * the property won't be propagated to the [DataCaptureOrchestrator].
 *
 * Data will then automatically be captured by the SDK.
 */
internal interface DataSourceModule {
    /**
     * Returns a list of all the data sources that are defined in this module.
     */
    fun getDataSources(): List<DataSourceState<*>>

    val breadcrumbDataSource: DataSourceState<BreadcrumbDataSource>
    val viewDataSource: DataSourceState<ViewDataSource>
    val tapDataSource: DataSourceState<TapDataSource>
    val webViewUrlDataSource: DataSourceState<WebViewUrlDataSource>
    val pushNotificationDataSource: DataSourceState<PushNotificationDataSource>
    val sessionPropertiesDataSource: DataSourceState<SessionPropertiesDataSource>
}

internal class DataSourceModuleImpl(
    initModule: InitModule,
    otelModule: OpenTelemetryModule,
    essentialServiceModule: EssentialServiceModule,
    @Suppress("UNUSED_PARAMETER") systemServiceModule: SystemServiceModule,
    @Suppress("UNUSED_PARAMETER") androidServicesModule: AndroidServicesModule,
    @Suppress("UNUSED_PARAMETER") workerThreadModule: WorkerThreadModule,
) : DataSourceModule {

    private val values: MutableList<DataSourceState<*>> = mutableListOf()

    override val breadcrumbDataSource: DataSourceState<BreadcrumbDataSource> by dataSourceState {
        DataSourceState(
            factory = {
                BreadcrumbDataSource(
                    breadcrumbBehavior = essentialServiceModule.configService.breadcrumbBehavior,
                    writer = otelModule.currentSessionSpan,
                    logger = initModule.logger
                )
            }
        )
    }

    override val tapDataSource: DataSourceState<TapDataSource> by dataSourceState {
        DataSourceState(
            factory = {
                TapDataSource(
                    breadcrumbBehavior = essentialServiceModule.configService.breadcrumbBehavior,
                    writer = otelModule.currentSessionSpan,
                    logger = initModule.logger
                )
            }
        )
    }

    override val pushNotificationDataSource: DataSourceState<PushNotificationDataSource> by dataSourceState {
        DataSourceState(
            factory = {
                PushNotificationDataSource(
                    breadcrumbBehavior = essentialServiceModule.configService.breadcrumbBehavior,
                    initModule.clock,
                    writer = otelModule.currentSessionSpan,
                    logger = initModule.logger
                )
            }
        )
    }

    override val viewDataSource: DataSourceState<ViewDataSource> by dataSourceState {
        DataSourceState(
            factory = {
                ViewDataSource(
                    configService.breadcrumbBehavior,
                    initModule.clock,
                    otelModule.spanService,
                    initModule.logger
                )
            },
            configGate = { configService.breadcrumbBehavior.isActivityBreadcrumbCaptureEnabled() }
        )
    }

    override val webViewUrlDataSource: DataSourceState<WebViewUrlDataSource> by dataSourceState {
        DataSourceState(
            factory = {
                WebViewUrlDataSource(
                    configService.breadcrumbBehavior,
                    otelModule.currentSessionSpan,
                    initModule.logger
                )
            },
            configGate = { configService.breadcrumbBehavior.isWebViewBreadcrumbCaptureEnabled() }
        )
    }

    override val sessionPropertiesDataSource: DataSourceState<SessionPropertiesDataSource> by dataSourceState {
        DataSourceState(
            factory = {
                SessionPropertiesDataSource(
                    sessionBehavior = configService.sessionBehavior,
                    writer = otelModule.currentSessionSpan,
                    logger = initModule.logger
                )
            }
        )
    }

    /* Implementation details */

    private val configService = essentialServiceModule.configService
    override fun getDataSources(): List<DataSourceState<*>> = values

    /**
     * Property delegate that adds the value to a
     * list on its creation. That list is then used by the [DataCaptureOrchestrator] to control
     * the data sources.
     */
    @Suppress("unused")
    private fun <T : DataSource<*>> dataSourceState(provider: Provider<DataSourceState<T>>) =
        DataSourceDelegate(provider = provider, values = values)
}

private class DataSourceDelegate<S : DataSource<*>>(
    provider: Provider<DataSourceState<S>>,
    values: MutableList<DataSourceState<*>>,
) : ReadOnlyProperty<Any?, DataSourceState<S>> {

    private val value: DataSourceState<S> = provider()

    init {
        values.add(value)
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>) = value
}
