package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.config.behavior.AnrBehavior
import io.embrace.android.embracesdk.internal.config.behavior.AnrBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.AppExitInfoBehavior
import io.embrace.android.embracesdk.internal.config.behavior.AppExitInfoBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.AutoDataCaptureBehavior
import io.embrace.android.embracesdk.internal.config.behavior.AutoDataCaptureBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.BackgroundActivityBehavior
import io.embrace.android.embracesdk.internal.config.behavior.BackgroundActivityBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.BehaviorThresholdCheck
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
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.SessionBehavior
import io.embrace.android.embracesdk.internal.config.behavior.SessionBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.StartupBehavior
import io.embrace.android.embracesdk.internal.config.behavior.StartupBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.WebViewVitalsBehavior
import io.embrace.android.embracesdk.internal.config.behavior.WebViewVitalsBehaviorImpl
import io.embrace.android.embracesdk.internal.config.local.AnrLocalConfig
import io.embrace.android.embracesdk.internal.config.local.AppExitInfoLocalConfig
import io.embrace.android.embracesdk.internal.config.local.BackgroundActivityLocalConfig
import io.embrace.android.embracesdk.internal.config.local.BaseUrlLocalConfig
import io.embrace.android.embracesdk.internal.config.local.LocalConfig
import io.embrace.android.embracesdk.internal.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.internal.config.local.SessionLocalConfig
import io.embrace.android.embracesdk.internal.config.local.StartupMomentLocalConfig
import io.embrace.android.embracesdk.internal.config.remote.AnrRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.LogRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.NetworkSpanForwardingRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.Uuid

private val behaviorThresholdCheck = BehaviorThresholdCheck(Uuid::getEmbUuid)

/**
 * A [AnrBehavior] that returns default values.
 */
fun createAnrBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    localCfg: Provider<AnrLocalConfig?> = { null },
    remoteCfg: Provider<AnrRemoteConfig?> = { null }
): AnrBehavior = AnrBehaviorImpl(thresholdCheck, localCfg, remoteCfg)

/**
 * A [SessionBehaviorImpl] that returns default values.
 */
fun createSessionBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    localCfg: Provider<SessionLocalConfig?> = { null },
    remoteCfg: Provider<RemoteConfig?> = { null }
): SessionBehavior = SessionBehaviorImpl(thresholdCheck, localCfg, remoteCfg)

/**
 * A [NetworkBehaviorImpl] that returns default values.
 */
fun createNetworkBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    localCfg: Provider<SdkLocalConfig?> = { null },
    remoteCfg: Provider<RemoteConfig?> = { null }
): NetworkBehavior = NetworkBehaviorImpl(thresholdCheck, localCfg, remoteCfg)

/**
 * A [BackgroundActivityBehaviorImpl] that returns default values.
 */
fun createBackgroundActivityBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    localCfg: Provider<BackgroundActivityLocalConfig?> = { null },
    remoteCfg: Provider<BackgroundActivityRemoteConfig?> = { null }
): BackgroundActivityBehavior = BackgroundActivityBehaviorImpl(thresholdCheck, localCfg, remoteCfg)

/**
 * A [AutoDataCaptureBehaviorImpl] that returns default values.
 */
fun createAutoDataCaptureBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    localCfg: Provider<LocalConfig?> = { null },
    remoteCfg: Provider<RemoteConfig?> = { null }
): AutoDataCaptureBehavior = AutoDataCaptureBehaviorImpl(thresholdCheck, localCfg, remoteCfg)

/**
 * A [LogMessageBehaviorImpl] that returns default values.
 */
fun createLogMessageBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    remoteCfg: Provider<LogRemoteConfig?> = { null }
): LogMessageBehavior = LogMessageBehaviorImpl(thresholdCheck, remoteCfg)

/**
 * A [StartupBehaviorImpl] that returns default values.
 */
fun createStartupBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    localCfg: Provider<StartupMomentLocalConfig?> = { null }
): StartupBehavior = StartupBehaviorImpl(thresholdCheck, localCfg)

/**
 * A [DataCaptureEventBehaviorImpl] that returns default values.
 */
fun createDataCaptureEventBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    remoteCfg: Provider<RemoteConfig?> = { null }
): DataCaptureEventBehavior = DataCaptureEventBehaviorImpl(thresholdCheck, remoteCfg)

/**
 * A [SdkModeBehaviorImpl] that returns default values.
 */
fun createSdkModeBehavior(
    isDebug: Boolean = false,
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    localCfg: Provider<LocalConfig?> = { null },
    remoteCfg: Provider<RemoteConfig?> = { null }
): SdkModeBehavior = SdkModeBehaviorImpl(isDebug, thresholdCheck, localCfg, remoteCfg)

/**
 * A [SdkModeBehaviorImpl] that returns default values.
 */
fun createSdkEndpointBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    localCfg: Provider<BaseUrlLocalConfig?> = { null },
): SdkEndpointBehavior = SdkEndpointBehaviorImpl(thresholdCheck, localCfg)

/**
 * A [AppExitInfoBehavior] that returns default values.
 */
fun createAppExitInfoBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    localCfg: Provider<AppExitInfoLocalConfig?> = { null },
    remoteCfg: Provider<RemoteConfig?> = { null },
): AppExitInfoBehavior = AppExitInfoBehaviorImpl(thresholdCheck, localCfg, remoteCfg)

/**
 * A [NetworkSpanForwardingBehaviorImpl] that returns default values.
 */
fun createNetworkSpanForwardingBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    remoteConfig: Provider<NetworkSpanForwardingRemoteConfig?> = { null }
): NetworkSpanForwardingBehavior = NetworkSpanForwardingBehaviorImpl(thresholdCheck, remoteConfig)

/**
 * A [WebViewVitalsBehaviorImpl] that returns default values.
 */
fun createWebViewVitalsBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    remoteCfg: Provider<RemoteConfig?> = { null },
): WebViewVitalsBehavior = WebViewVitalsBehaviorImpl(thresholdCheck, remoteCfg)

/**
 * A [SensitiveKeysBehaviorImpl] that returns default values.
 */
internal fun createSensitiveKeysBehavior() = SensitiveKeysBehaviorImpl(
    SdkLocalConfig()
)
