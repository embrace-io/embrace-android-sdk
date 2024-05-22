package io.embrace.android.embracesdk.injection

import android.os.Build
import io.embrace.android.embracesdk.anr.sigquit.SigquitDataSource
import io.embrace.android.embracesdk.arch.DataCaptureOrchestrator
import io.embrace.android.embracesdk.arch.datasource.DataSource
import io.embrace.android.embracesdk.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.capture.aei.AeiDataSource
import io.embrace.android.embracesdk.capture.aei.AeiDataSourceImpl
import io.embrace.android.embracesdk.capture.connectivity.NetworkStatusDataSource
import io.embrace.android.embracesdk.capture.crumbs.BreadcrumbDataSource
import io.embrace.android.embracesdk.capture.crumbs.PushNotificationDataSource
import io.embrace.android.embracesdk.capture.crumbs.RnActionDataSource
import io.embrace.android.embracesdk.capture.crumbs.TapDataSource
import io.embrace.android.embracesdk.capture.crumbs.ViewDataSource
import io.embrace.android.embracesdk.capture.crumbs.WebViewUrlDataSource
import io.embrace.android.embracesdk.capture.memory.MemoryWarningDataSource
import io.embrace.android.embracesdk.capture.powersave.LowPowerDataSource
import io.embrace.android.embracesdk.capture.session.SessionPropertiesDataSource
import io.embrace.android.embracesdk.capture.thermalstate.ThermalStateDataSource
import io.embrace.android.embracesdk.capture.webview.WebViewDataSource
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.worker.WorkerName
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
    val applicationExitInfoDataSource: DataSourceState<AeiDataSource>?
    val lowPowerDataSource: DataSourceState<LowPowerDataSource>
    val memoryWarningDataSource: DataSourceState<MemoryWarningDataSource>
    val networkStatusDataSource: DataSourceState<NetworkStatusDataSource>
    val sigquitDataSource: DataSourceState<SigquitDataSource>
    val rnActionDataSource: DataSourceState<RnActionDataSource>
    val thermalStateDataSource: DataSourceState<ThermalStateDataSource>?
    val webViewDataSource: DataSourceState<WebViewDataSource>
}

internal class DataSourceModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
    otelModule: OpenTelemetryModule,
    essentialServiceModule: EssentialServiceModule,
    systemServiceModule: SystemServiceModule,
    androidServicesModule: AndroidServicesModule,
    workerThreadModule: WorkerThreadModule,
    anrModule: AnrModule
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

    override val memoryWarningDataSource: DataSourceState<MemoryWarningDataSource> by dataSourceState {
        DataSourceState(
            factory = {
                MemoryWarningDataSource(
                    sessionSpanWriter = otelModule.currentSessionSpan,
                    logger = initModule.logger,
                )
            },
            configGate = { configService.autoDataCaptureBehavior.isMemoryServiceEnabled() }
        )
    }

    override val applicationExitInfoDataSource: DataSourceState<AeiDataSource>? by dataSourceState {
        DataSourceState(
            factory = { aeiService },
            configGate = { configService.isAppExitInfoCaptureEnabled() }
        )
    }

    private val aeiService: AeiDataSourceImpl? by singleton {
        if (BuildVersionChecker.isAtLeast(Build.VERSION_CODES.R)) {
            AeiDataSourceImpl(
                workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION),
                essentialServiceModule.configService.appExitInfoBehavior,
                systemServiceModule.activityManager,
                androidServicesModule.preferencesService,
                essentialServiceModule.metadataService,
                essentialServiceModule.sessionIdTracker,
                essentialServiceModule.userService,
                essentialServiceModule.logWriter,
                initModule.logger
            )
        } else {
            null
        }
    }

    override val lowPowerDataSource: DataSourceState<LowPowerDataSource> by dataSourceState {
        DataSourceState(
            factory = {
                LowPowerDataSource(
                    context = coreModule.context,
                    backgroundWorker = workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION),
                    clock = initModule.clock,
                    provider = { systemServiceModule.powerManager },
                    spanService = otelModule.spanService,
                    logger = initModule.logger
                )
            },
            configGate = { configService.autoDataCaptureBehavior.isPowerSaveModeServiceEnabled() }
        )
    }

    override val networkStatusDataSource: DataSourceState<NetworkStatusDataSource> by dataSourceState {
        DataSourceState(
            factory = {
                NetworkStatusDataSource(
                    spanService = otelModule.spanService,
                    logger = initModule.logger
                )
            },
            configGate = { configService.autoDataCaptureBehavior.isNetworkConnectivityServiceEnabled() }
        )
    }

    override val sigquitDataSource: DataSourceState<SigquitDataSource> by dataSourceState {
        DataSourceState(
            factory = anrModule::sigquitDataSource,
            configGate = { configService.anrBehavior.isGoogleAnrCaptureEnabled() }
        )
    }

    override val rnActionDataSource: DataSourceState<RnActionDataSource> by dataSourceState {
        DataSourceState(
            factory = {
                RnActionDataSource(
                    breadcrumbBehavior = configService.breadcrumbBehavior,
                    otelModule.spanService,
                    initModule.logger
                )
            }
        )
    }

    private val thermalService: ThermalStateDataSource? by singleton {
        if (BuildVersionChecker.isAtLeast(Build.VERSION_CODES.Q)) {
            ThermalStateDataSource(
                spanService = otelModule.spanService,
                logger = initModule.logger,
                backgroundWorker = workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION),
                clock = initModule.clock,
                powerManagerProvider = { systemServiceModule.powerManager }
            )
        } else {
            null
        }
    }

    override val thermalStateDataSource: DataSourceState<ThermalStateDataSource>? by dataSourceState {
        DataSourceState(
            factory = { thermalService },
            configGate = {
                configService.autoDataCaptureBehavior.isThermalStatusCaptureEnabled() &&
                    configService.sdkModeBehavior.isBetaFeaturesEnabled()
            }
        )
    }

    override val webViewDataSource: DataSourceState<WebViewDataSource> by dataSourceState {
        DataSourceState(
            factory = {
                WebViewDataSource(
                    webViewVitalsBehavior = essentialServiceModule.configService.webViewVitalsBehavior,
                    writer = otelModule.currentSessionSpan,
                    logger = initModule.logger,
                    serializer = coreModule.jsonSerializer
                )
            },
            configGate = { configService.webViewVitalsBehavior.isWebViewVitalsEnabled() }
        )
    }

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
