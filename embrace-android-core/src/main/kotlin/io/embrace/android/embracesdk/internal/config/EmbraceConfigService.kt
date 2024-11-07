package io.embrace.android.embracesdk.internal.config

import io.embrace.android.embracesdk.internal.config.behavior.AnrBehavior
import io.embrace.android.embracesdk.internal.config.behavior.AnrBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.AppExitInfoBehavior
import io.embrace.android.embracesdk.internal.config.behavior.AppExitInfoBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.AutoDataCaptureBehavior
import io.embrace.android.embracesdk.internal.config.behavior.AutoDataCaptureBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.BackgroundActivityBehavior
import io.embrace.android.embracesdk.internal.config.behavior.BackgroundActivityBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.BehaviorThresholdCheck
import io.embrace.android.embracesdk.internal.config.behavior.BreadcrumbBehavior
import io.embrace.android.embracesdk.internal.config.behavior.BreadcrumbBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.DataCaptureEventBehavior
import io.embrace.android.embracesdk.internal.config.behavior.DataCaptureEventBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.LogMessageBehavior
import io.embrace.android.embracesdk.internal.config.behavior.LogMessageBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.NetworkBehavior
import io.embrace.android.embracesdk.internal.config.behavior.NetworkBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.NetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.internal.config.behavior.NetworkSpanForwardingBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.SdkEndpointBehavior
import io.embrace.android.embracesdk.internal.config.behavior.SdkEndpointBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.SdkModeBehavior
import io.embrace.android.embracesdk.internal.config.behavior.SdkModeBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehavior
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.SessionBehavior
import io.embrace.android.embracesdk.internal.config.behavior.SessionBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.WebViewVitalsBehavior
import io.embrace.android.embracesdk.internal.config.behavior.WebViewVitalsBehaviorImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.opentelemetry.OpenTelemetryConfiguration
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Loads configuration for the app from the Embrace API.
 */
internal class EmbraceConfigService(
    openTelemetryCfg: OpenTelemetryConfiguration,
    preferencesService: PreferencesService,
    suppliedFramework: AppFramework,
    configProvider: Provider<RemoteConfig?>,
    thresholdCheck: BehaviorThresholdCheck = BehaviorThresholdCheck { preferencesService.deviceIdentifier },
    instrumentedConfig: InstrumentedConfig,
) : ConfigService {

    override val backgroundActivityBehavior: BackgroundActivityBehavior =
        BackgroundActivityBehaviorImpl(
            thresholdCheck = thresholdCheck,
            remoteSupplier = { configProvider()?.backgroundActivityConfig },
            instrumentedConfig = instrumentedConfig
        )

    override val autoDataCaptureBehavior: AutoDataCaptureBehavior =
        AutoDataCaptureBehaviorImpl(
            thresholdCheck = thresholdCheck,
            remoteSupplier = configProvider,
            instrumentedConfig = instrumentedConfig
        )

    override val breadcrumbBehavior: BreadcrumbBehavior =
        BreadcrumbBehaviorImpl(
            thresholdCheck,
            remoteSupplier = configProvider,
            instrumentedConfig = instrumentedConfig
        )

    override val sensitiveKeysBehavior: SensitiveKeysBehavior =
        SensitiveKeysBehaviorImpl(instrumentedConfig = instrumentedConfig)

    override val logMessageBehavior: LogMessageBehavior =
        LogMessageBehaviorImpl(
            thresholdCheck,
            remoteSupplier = { configProvider()?.logConfig }
        )

    override val anrBehavior: AnrBehavior =
        AnrBehaviorImpl(
            thresholdCheck,
            remoteSupplier = { configProvider()?.anrConfig },
            instrumentedConfig = instrumentedConfig
        )

    override val sessionBehavior: SessionBehavior = SessionBehaviorImpl(
        thresholdCheck,
        remoteSupplier = configProvider,
        instrumentedConfig = instrumentedConfig
    )

    override val networkBehavior: NetworkBehavior =
        NetworkBehaviorImpl(
            thresholdCheck = thresholdCheck,
            remoteSupplier = configProvider,
            instrumentedConfig = instrumentedConfig
        )

    override val dataCaptureEventBehavior: DataCaptureEventBehavior = DataCaptureEventBehaviorImpl(
        thresholdCheck = thresholdCheck,
        remoteSupplier = configProvider
    )

    override val sdkModeBehavior: SdkModeBehavior = SdkModeBehaviorImpl(
        thresholdCheck = thresholdCheck,
        remoteSupplier = configProvider
    )

    override val sdkEndpointBehavior: SdkEndpointBehavior = SdkEndpointBehaviorImpl(
        thresholdCheck = thresholdCheck,
        instrumentedConfig = instrumentedConfig
    )

    override val appExitInfoBehavior: AppExitInfoBehavior = AppExitInfoBehaviorImpl(
        thresholdCheck = thresholdCheck,
        remoteSupplier = configProvider,
        instrumentedConfig = instrumentedConfig
    )

    override val networkSpanForwardingBehavior: NetworkSpanForwardingBehavior =
        NetworkSpanForwardingBehaviorImpl(
            thresholdCheck = thresholdCheck,
            remoteSupplier = { configProvider()?.networkSpanForwardingRemoteConfig },
            instrumentedConfig = instrumentedConfig
        )

    override val webViewVitalsBehavior: WebViewVitalsBehavior =
        WebViewVitalsBehaviorImpl(
            thresholdCheck = thresholdCheck,
            remoteSupplier = configProvider
        )

    override val appId: String? = resolveAppId(instrumentedConfig.project.getAppId(), openTelemetryCfg)

    override fun isOnlyUsingOtelExporters(): Boolean = appId.isNullOrEmpty()

    /**
     * Loads the build information from resources provided by the config file packaged within the application by Gradle at
     * build-time.
     *
     * @return the local configuration
     */
    fun resolveAppId(id: String?, openTelemetryCfg: OpenTelemetryConfiguration): String? {
        require(!id.isNullOrEmpty() || openTelemetryCfg.hasConfiguredOtelExporters()) {
            "No appId supplied in embrace-config.json. This is required if you want to " +
                "send data to Embrace, unless you configure an OTel exporter and add" +
                " embrace.disableMappingFileUpload=true to gradle.properties."
        }
        return id
    }

    override val appFramework: AppFramework = instrumentedConfig.project.getAppFramework()?.let {
        AppFramework.fromString(it)
    } ?: suppliedFramework
}
