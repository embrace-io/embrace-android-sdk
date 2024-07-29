package io.embrace.android.embracesdk.internal.capture

import android.os.Build
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.capture.crumbs.BreadcrumbDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.PushNotificationDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.RnActionDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.TapDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.ViewDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.WebViewUrlDataSource
import io.embrace.android.embracesdk.internal.capture.memory.MemoryWarningDataSource
import io.embrace.android.embracesdk.internal.capture.powersave.LowPowerDataSource
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesDataSource
import io.embrace.android.embracesdk.internal.capture.thermalstate.ThermalStateDataSource
import io.embrace.android.embracesdk.internal.capture.webview.WebViewDataSource
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.injection.CoreModule
import io.embrace.android.embracesdk.internal.injection.InitModule
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.injection.SystemServiceModule
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.singleton
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.worker.WorkerName

public class FeatureModuleImpl(
    coreModule: CoreModule,
    initModule: InitModule,
    otelModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    systemServiceModule: SystemServiceModule,
    configService: ConfigService
) : FeatureModule {

    override val memoryWarningDataSource: DataSourceState<MemoryWarningDataSource> by singleton {
        DataSourceState(
            factory = {
                MemoryWarningDataSource(
                    application = coreModule.application,
                    clock = initModule.clock,
                    sessionSpanWriter = otelModule.currentSessionSpan,
                    logger = initModule.logger,
                )
            },
            configGate = { configService.autoDataCaptureBehavior.isMemoryServiceEnabled() },
        )
    }

    override val breadcrumbDataSource: DataSourceState<BreadcrumbDataSource> by singleton {
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

    override val viewDataSource: DataSourceState<ViewDataSource> by singleton {
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

    override val pushNotificationDataSource: DataSourceState<PushNotificationDataSource> by singleton {
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

    override val tapDataSource: DataSourceState<TapDataSource> by singleton {
        DataSourceState(
            factory = {
                TapDataSource(
                    breadcrumbBehavior = configService.breadcrumbBehavior,
                    writer = otelModule.currentSessionSpan,
                    logger = initModule.logger
                )
            }
        )
    }

    override val webViewUrlDataSource: DataSourceState<WebViewUrlDataSource> by singleton {
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

    override val rnActionDataSource: DataSourceState<RnActionDataSource> by singleton {
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

    override val sessionPropertiesDataSource: DataSourceState<SessionPropertiesDataSource> by singleton {
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

    override val webViewDataSource: DataSourceState<WebViewDataSource> by singleton {
        DataSourceState(
            factory = {
                WebViewDataSource(
                    webViewVitalsBehavior = configService.webViewVitalsBehavior,
                    writer = otelModule.currentSessionSpan,
                    logger = initModule.logger,
                    serializer = initModule.jsonSerializer
                )
            },
            configGate = { configService.webViewVitalsBehavior.isWebViewVitalsEnabled() }
        )
    }

    override val lowPowerDataSource: DataSourceState<LowPowerDataSource> by singleton {
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

    override val thermalStateDataSource: DataSourceState<ThermalStateDataSource> by singleton {
        DataSourceState(
            factory = { thermalService },
            configGate = {
                configService.autoDataCaptureBehavior.isThermalStatusCaptureEnabled() &&
                    configService.sdkModeBehavior.isBetaFeaturesEnabled()
            }
        )
    }
}
