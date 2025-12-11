package io.embrace.android.embracesdk.internal.config

import io.embrace.android.embracesdk.internal.config.behavior.AppExitInfoBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.AutoDataCaptureBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.BackgroundActivityBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.BehaviorThresholdCheck
import io.embrace.android.embracesdk.internal.config.behavior.BreadcrumbBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.DataCaptureEventBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.LogMessageBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.NetworkBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.NetworkSpanForwardingBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.OtelBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.SdkModeBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.SessionBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.ThreadBlockageBehaviorImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.payload.AppFramework

/**
 * Loads configuration for the app from the Embrace API.
 */
class ConfigServiceImpl(
    instrumentedConfig: InstrumentedConfig,
    override val remoteConfig: RemoteConfig?,
    deviceIdSupplier: () -> String,
    private val hasConfiguredOtelExporters: () -> Boolean,
) : ConfigService, HybridSdkConfigService {

    private val thresholdCheck: BehaviorThresholdCheck = BehaviorThresholdCheck(deviceIdSupplier)
    override val backgroundActivityBehavior =
        BackgroundActivityBehaviorImpl(thresholdCheck, instrumentedConfig, remoteConfig)
    override val autoDataCaptureBehavior =
        AutoDataCaptureBehaviorImpl(thresholdCheck, instrumentedConfig, remoteConfig)
    override val breadcrumbBehavior = BreadcrumbBehaviorImpl(instrumentedConfig, remoteConfig)
    override val sensitiveKeysBehavior = SensitiveKeysBehaviorImpl(instrumentedConfig)
    override val logMessageBehavior = LogMessageBehaviorImpl(remoteConfig)
    override val threadBlockageBehavior = ThreadBlockageBehaviorImpl(thresholdCheck, remoteConfig)
    override val sessionBehavior = SessionBehaviorImpl(remoteConfig)
    override val networkBehavior = NetworkBehaviorImpl(instrumentedConfig, remoteConfig)
    override val dataCaptureEventBehavior = DataCaptureEventBehaviorImpl(remoteConfig)
    override val sdkModeBehavior = SdkModeBehaviorImpl(thresholdCheck, remoteConfig)
    override val appExitInfoBehavior =
        AppExitInfoBehaviorImpl(thresholdCheck, instrumentedConfig, remoteConfig)
    override val networkSpanForwardingBehavior =
        NetworkSpanForwardingBehaviorImpl(thresholdCheck, instrumentedConfig, remoteConfig)
    override val otelBehavior = OtelBehaviorImpl(thresholdCheck, instrumentedConfig, remoteConfig)

    override val appId: String? = run {
        val id = instrumentedConfig.project.getAppId()
        require(!id.isNullOrEmpty() || hasConfiguredOtelExporters()) {
            "No appId supplied in embrace-config.json. This is required if you want to " +
                "send data to Embrace, unless you configure an OTel exporter and add" +
                " embrace.disableMappingFileUpload=true to gradle.properties."
        }
        id
    }

    override fun isOnlyUsingOtelExporters(): Boolean = appId.isNullOrEmpty()

    override val appFramework: AppFramework = instrumentedConfig.project.getAppFramework()?.let {
        AppFramework.fromString(it)
    } ?: AppFramework.NATIVE

    override fun isBehaviorEnabled(pctEnabled: Float?): Boolean? = thresholdCheck.isBehaviorEnabled(pctEnabled)
}
