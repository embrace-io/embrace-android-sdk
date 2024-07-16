package io.embrace.android.embracesdk.fakes

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
import io.embrace.android.embracesdk.internal.config.behavior.SdkEndpointBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.SdkModeBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.SessionBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.StartupBehaviorImpl
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

private val behaviorThresholdCheck = BehaviorThresholdCheck { Uuid.getEmbUuid() }

/**
 * A fake [AnrBehavior] that returns default values.
 */
internal fun fakeAnrBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    localCfg: Provider<AnrLocalConfig?> = { null },
    remoteCfg: Provider<AnrRemoteConfig?> = { null }
) = AnrBehaviorImpl(thresholdCheck, localCfg, remoteCfg)

/**
 * A fake [SessionBehaviorImpl] that returns default values.
 */
internal fun fakeSessionBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    localCfg: Provider<SessionLocalConfig?> = { null },
    remoteCfg: Provider<RemoteConfig?> = { null }
) = SessionBehaviorImpl(thresholdCheck, localCfg, remoteCfg)

/**
 * A fake [NetworkBehaviorImpl] that returns default values.
 */
internal fun fakeNetworkBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    localCfg: Provider<SdkLocalConfig?> = { null },
    remoteCfg: Provider<RemoteConfig?> = { null }
) = NetworkBehaviorImpl(thresholdCheck, localCfg, remoteCfg)

/**
 * A fake [BackgroundActivityBehaviorImpl] that returns default values.
 */
internal fun fakeBackgroundActivityBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    localCfg: Provider<BackgroundActivityLocalConfig?> = { null },
    remoteCfg: Provider<BackgroundActivityRemoteConfig?> = { null }
) = BackgroundActivityBehaviorImpl(thresholdCheck, localCfg, remoteCfg)

/**
 * A fake [AutoDataCaptureBehaviorImpl] that returns default values.
 */
internal fun fakeAutoDataCaptureBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    localCfg: Provider<LocalConfig?> = { null },
    remoteCfg: Provider<RemoteConfig?> = { null }
) = AutoDataCaptureBehaviorImpl(thresholdCheck, localCfg, remoteCfg)

/**
 * A fake [BreadcrumbBehaviorImpl] that returns default values.
 */
internal fun fakeBreadcrumbBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    localCfg: Provider<SdkLocalConfig?> = { null },
    remoteCfg: Provider<RemoteConfig?> = { null }
) = BreadcrumbBehaviorImpl(thresholdCheck, localCfg, remoteCfg)

/**
 * A fake [LogMessageBehaviorImpl] that returns default values.
 */
internal fun fakeLogMessageBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    remoteCfg: Provider<LogRemoteConfig?> = { null }
) = LogMessageBehaviorImpl(thresholdCheck, remoteCfg)

/**
 * A fake [StartupBehaviorImpl] that returns default values.
 */
internal fun fakeStartupBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    localCfg: Provider<StartupMomentLocalConfig?> = { null }
) = StartupBehaviorImpl(thresholdCheck, localCfg)

/**
 * A fake [DataCaptureEventBehaviorImpl] that returns default values.
 */
internal fun fakeDataCaptureEventBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    remoteCfg: Provider<RemoteConfig?> = { null }
) = DataCaptureEventBehaviorImpl(thresholdCheck, remoteCfg)

/**
 * A fake [SdkModeBehaviorImpl] that returns default values.
 */
internal fun fakeSdkModeBehavior(
    isDebug: Boolean = false,
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    localCfg: Provider<LocalConfig?> = { null },
    remoteCfg: Provider<RemoteConfig?> = { null }
) = SdkModeBehaviorImpl(isDebug, thresholdCheck, localCfg, remoteCfg)

/**
 * A fake [SdkModeBehaviorImpl] that returns default values.
 */
internal fun fakeSdkEndpointBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    localCfg: Provider<BaseUrlLocalConfig?> = { null },
) = SdkEndpointBehaviorImpl(thresholdCheck, localCfg)

/**
 * A fake [AppExitInfoBehavior] that returns default values.
 */
internal fun fakeAppExitInfoBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    localCfg: Provider<AppExitInfoLocalConfig?> = { null },
    remoteCfg: Provider<RemoteConfig?> = { null },
) = AppExitInfoBehaviorImpl(thresholdCheck, localCfg, remoteCfg)

/**
 * A fake [NetworkSpanForwardingBehaviorImpl] that returns default values.
 */
internal fun fakeNetworkSpanForwardingBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    remoteConfig: Provider<NetworkSpanForwardingRemoteConfig?> = { null }
) = NetworkSpanForwardingBehaviorImpl(thresholdCheck, remoteConfig)

/**
 * A fake [WebViewVitalsBehaviorImpl] that returns default values.
 */
internal fun fakeWebViewVitalsBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    remoteCfg: Provider<RemoteConfig?> = { null },
) = WebViewVitalsBehaviorImpl(thresholdCheck, remoteCfg)
