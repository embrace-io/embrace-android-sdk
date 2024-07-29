package io.embrace.android.embracesdk.internal.capture

import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.capture.crumbs.BreadcrumbDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.PushNotificationDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.RnActionDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.TapDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.ViewDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.WebViewUrlDataSource
import io.embrace.android.embracesdk.internal.capture.memory.MemoryWarningDataSource
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesDataSource
import io.embrace.android.embracesdk.internal.capture.webview.WebViewDataSource
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.injection.CoreModule
import io.embrace.android.embracesdk.internal.injection.InitModule
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.injection.singleton

public class FeatureModuleImpl(
    coreModule: CoreModule,
    initModule: InitModule,
    otelModule: OpenTelemetryModule,
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
}
