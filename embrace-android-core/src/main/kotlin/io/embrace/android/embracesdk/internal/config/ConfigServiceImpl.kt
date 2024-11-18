package io.embrace.android.embracesdk.internal.config

import io.embrace.android.embracesdk.internal.config.behavior.AnrBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.AppExitInfoBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.AutoDataCaptureBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.BackgroundActivityBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.BehaviorThresholdCheck
import io.embrace.android.embracesdk.internal.config.behavior.BreadcrumbBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.DataCaptureEventBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.LogMessageBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.NetworkBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.NetworkSpanForwardingBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.SdkModeBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.SessionBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.WebViewVitalsBehaviorImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.opentelemetry.OpenTelemetryConfiguration
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.prefs.PreferencesService

/**
 * Loads configuration for the app from the Embrace API.
 */
internal class ConfigServiceImpl(
    openTelemetryCfg: OpenTelemetryConfiguration,
    preferencesService: PreferencesService,
    suppliedFramework: AppFramework,
    instrumentedConfig: InstrumentedConfig,
    remoteConfig: RemoteConfig?,
    thresholdCheck: BehaviorThresholdCheck = BehaviorThresholdCheck { preferencesService.deviceIdentifier },
) : ConfigService {

    override val backgroundActivityBehavior =
        BackgroundActivityBehaviorImpl(thresholdCheck, instrumentedConfig, remoteConfig)
    override val autoDataCaptureBehavior = AutoDataCaptureBehaviorImpl(thresholdCheck, instrumentedConfig, remoteConfig)
    override val breadcrumbBehavior = BreadcrumbBehaviorImpl(instrumentedConfig, remoteConfig)
    override val sensitiveKeysBehavior = SensitiveKeysBehaviorImpl(instrumentedConfig)
    override val logMessageBehavior = LogMessageBehaviorImpl(remoteConfig)
    override val anrBehavior = AnrBehaviorImpl(thresholdCheck, instrumentedConfig, remoteConfig)
    override val sessionBehavior = SessionBehaviorImpl(instrumentedConfig, remoteConfig)
    override val networkBehavior = NetworkBehaviorImpl(instrumentedConfig, remoteConfig)
    override val dataCaptureEventBehavior = DataCaptureEventBehaviorImpl(remoteConfig)
    override val sdkModeBehavior = SdkModeBehaviorImpl(thresholdCheck, remoteConfig)
    override val appExitInfoBehavior = AppExitInfoBehaviorImpl(thresholdCheck, instrumentedConfig, remoteConfig)
    override val webViewVitalsBehavior = WebViewVitalsBehaviorImpl(thresholdCheck, remoteConfig)
    override val networkSpanForwardingBehavior =
        NetworkSpanForwardingBehaviorImpl(thresholdCheck, instrumentedConfig, remoteConfig)

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
