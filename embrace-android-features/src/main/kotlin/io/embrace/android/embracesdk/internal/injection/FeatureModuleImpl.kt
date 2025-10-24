package io.embrace.android.embracesdk.internal.injection

import android.os.Build
import io.embrace.android.embracesdk.internal.arch.DataCaptureOrchestrator
import io.embrace.android.embracesdk.internal.arch.EmbraceFeatureRegistry
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.arch.destination.LogWriter
import io.embrace.android.embracesdk.internal.capture.aei.AeiDataSource
import io.embrace.android.embracesdk.internal.capture.aei.AeiDataSourceImpl
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkStatusDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.BreadcrumbDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.PushNotificationDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.RnActionDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.ViewDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.WebViewUrlDataSource
import io.embrace.android.embracesdk.internal.capture.powersave.LowPowerDataSource
import io.embrace.android.embracesdk.internal.capture.telemetry.InternalErrorDataSource
import io.embrace.android.embracesdk.internal.capture.telemetry.InternalErrorDataSourceImpl
import io.embrace.android.embracesdk.internal.capture.thermalstate.ThermalStateDataSource
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.Worker

internal class FeatureModuleImpl(
    private val featureRegistry: EmbraceFeatureRegistry,
    coreModule: CoreModule,
    initModule: InitModule,
    otelModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    systemServiceModule: SystemServiceModule,
    androidServicesModule: AndroidServicesModule,
    logWriter: LogWriter,
    configService: ConfigService,
) : FeatureModule {

    override val breadcrumbDataSource: DataSourceState<BreadcrumbDataSource> by dataSourceState {
        DataSourceState(
            factory = {
                BreadcrumbDataSource(
                    breadcrumbBehavior = configService.breadcrumbBehavior,
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
            }
        )
    }

    override val pushNotificationDataSource: DataSourceState<PushNotificationDataSource> by dataSourceState {
        DataSourceState(
            factory = {
                PushNotificationDataSource(
                    breadcrumbBehavior = configService.breadcrumbBehavior,
                    initModule.clock,
                    writer = otelModule.currentSessionSpan,
                    logger = initModule.logger
                )
            }
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

    override val lowPowerDataSource: DataSourceState<LowPowerDataSource> by dataSourceState {
        DataSourceState(
            factory = {
                LowPowerDataSource(
                    context = coreModule.context,
                    backgroundWorker = workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker),
                    clock = initModule.clock,
                    provider = { systemServiceModule.powerManager },
                    spanService = otelModule.spanService,
                    logger = initModule.logger
                )
            },
            configGate = { configService.autoDataCaptureBehavior.isPowerSaveModeCaptureEnabled() }
        )
    }

    private val thermalService: ThermalStateDataSource? by singleton {
        if (BuildVersionChecker.isAtLeast(Build.VERSION_CODES.Q)) {
            ThermalStateDataSource(
                spanService = otelModule.spanService,
                logger = initModule.logger,
                backgroundWorker = workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker),
                clock = initModule.clock,
                powerManagerProvider = { systemServiceModule.powerManager }
            )
        } else {
            null
        }
    }

    override val thermalStateDataSource: DataSourceState<ThermalStateDataSource> by dataSourceState {
        DataSourceState(
            factory = { thermalService },
            configGate = {
                configService.autoDataCaptureBehavior.isThermalStatusCaptureEnabled()
            }
        )
    }

    private val aeiService: AeiDataSourceImpl? by singleton {
        val activityManager = systemServiceModule.activityManager
        if (BuildVersionChecker.isAtLeast(Build.VERSION_CODES.R) && activityManager != null) {
            AeiDataSourceImpl(
                workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker),
                configService,
                activityManager,
                androidServicesModule.preferencesService,
                logWriter,
                initModule.logger
            )
        } else {
            null
        }
    }

    override val applicationExitInfoDataSource: DataSourceState<AeiDataSource> by dataSourceState {
        DataSourceState(
            factory = { aeiService },
            configGate = { configService.appExitInfoBehavior.isAeiCaptureEnabled() }
        )
    }

    override val internalErrorDataSource: DataSourceState<InternalErrorDataSource> by dataSourceState {
        DataSourceState(
            factory = {
                InternalErrorDataSourceImpl(
                    logWriter = logWriter,
                    logger = initModule.logger,
                )
            },
            configGate = { configService.dataCaptureEventBehavior.isInternalExceptionCaptureEnabled() }
        )
    }

    override val networkStatusDataSource: DataSourceState<NetworkStatusDataSource> by dataSourceState {
        DataSourceState(
            factory = {
                NetworkStatusDataSource(
                    clock = initModule.clock,
                    spanService = otelModule.spanService,
                    logger = initModule.logger
                )
            },
            configGate = {
                configService.autoDataCaptureBehavior.isNetworkConnectivityCaptureEnabled()
            }
        )
    }

    /**
     * Property delegate that adds the value to a
     * list on its creation. That list is then used by the [DataCaptureOrchestrator] to control
     * the data sources.
     */
    @Suppress("unused")
    private fun <T : DataSource<*>> dataSourceState(provider: Provider<DataSourceState<T>>) =
        DataSourceDelegate(provider, featureRegistry)
}
