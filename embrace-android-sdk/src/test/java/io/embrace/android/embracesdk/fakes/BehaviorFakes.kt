package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.config.behavior.AnrBehavior
import io.embrace.android.embracesdk.config.behavior.AppExitInfoBehavior
import io.embrace.android.embracesdk.config.behavior.AutoDataCaptureBehavior
import io.embrace.android.embracesdk.config.behavior.BackgroundActivityBehavior
import io.embrace.android.embracesdk.config.behavior.BehaviorThresholdCheck
import io.embrace.android.embracesdk.config.behavior.BreadcrumbBehavior
import io.embrace.android.embracesdk.config.behavior.DataCaptureEventBehavior
import io.embrace.android.embracesdk.config.behavior.LogMessageBehavior
import io.embrace.android.embracesdk.config.behavior.NetworkBehavior
import io.embrace.android.embracesdk.config.behavior.NetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.config.behavior.SdkAppBehavior
import io.embrace.android.embracesdk.config.behavior.SdkEndpointBehavior
import io.embrace.android.embracesdk.config.behavior.SdkModeBehavior
import io.embrace.android.embracesdk.config.behavior.SessionBehavior
import io.embrace.android.embracesdk.config.behavior.SpansBehavior
import io.embrace.android.embracesdk.config.behavior.StartupBehavior
import io.embrace.android.embracesdk.config.behavior.WebViewVitalsBehavior
import io.embrace.android.embracesdk.config.local.AnrLocalConfig
import io.embrace.android.embracesdk.config.local.AppExitInfoLocalConfig
import io.embrace.android.embracesdk.config.local.BackgroundActivityLocalConfig
import io.embrace.android.embracesdk.config.local.BaseUrlLocalConfig
import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.config.local.SessionLocalConfig
import io.embrace.android.embracesdk.config.local.StartupMomentLocalConfig
import io.embrace.android.embracesdk.config.remote.AnrRemoteConfig
import io.embrace.android.embracesdk.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.config.remote.LogRemoteConfig
import io.embrace.android.embracesdk.config.remote.NetworkSpanForwardingRemoteConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.config.remote.SpansRemoteConfig
import io.embrace.android.embracesdk.internal.utils.Uuid

private val behaviorThresholdCheck = BehaviorThresholdCheck { Uuid.getEmbUuid() }

/**
 * A fake [AnrBehavior] that returns default values.
 */
internal fun fakeAnrBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    localCfg: () -> AnrLocalConfig? = { null },
    remoteCfg: () -> AnrRemoteConfig? = { null }
) = AnrBehavior(thresholdCheck, localCfg, remoteCfg)

/**
 * A fake [SessionBehavior] that returns default values.
 */
internal fun fakeSessionBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    localCfg: () -> SessionLocalConfig? = { null },
    remoteCfg: () -> RemoteConfig? = { null }
) = SessionBehavior(thresholdCheck, localCfg, remoteCfg)

/**
 * A fake [NetworkBehavior] that returns default values.
 */
internal fun fakeNetworkBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    localCfg: () -> SdkLocalConfig? = { null },
    remoteCfg: () -> RemoteConfig? = { null }
) = NetworkBehavior(thresholdCheck, localCfg, remoteCfg)

/**
 * A fake [BackgroundActivityBehavior] that returns default values.
 */
internal fun fakeBackgroundActivityBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    localCfg: () -> BackgroundActivityLocalConfig? = { null },
    remoteCfg: () -> BackgroundActivityRemoteConfig? = { null }
) = BackgroundActivityBehavior(thresholdCheck, localCfg, remoteCfg)

/**
 * A fake [AutoDataCaptureBehavior] that returns default values.
 */
internal fun fakeAutoDataCaptureBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    localCfg: () -> LocalConfig? = { null },
    remoteCfg: () -> RemoteConfig? = { null }
) = AutoDataCaptureBehavior(thresholdCheck, localCfg, remoteCfg)

/**
 * A fake [BreadcrumbBehavior] that returns default values.
 */
internal fun fakeBreadcrumbBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    localCfg: () -> SdkLocalConfig? = { null },
    remoteCfg: () -> RemoteConfig? = { null }
) = BreadcrumbBehavior(thresholdCheck, localCfg, remoteCfg)

/**
 * A fake [LogMessageBehavior] that returns default values.
 */
internal fun fakeLogMessageBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    remoteCfg: () -> LogRemoteConfig? = { null }
) = LogMessageBehavior(thresholdCheck, remoteCfg)

/**
 * A fake [SpanBehavior] that returns default values.
 */
internal fun fakeSpansBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    remoteConfig: () -> SpansRemoteConfig? = { null }
) = SpansBehavior(thresholdCheck, remoteConfig)

/**
 * A fake [StartupBehavior] that returns default values.
 */
internal fun fakeStartupBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    localCfg: () -> StartupMomentLocalConfig? = { null }
) = StartupBehavior(thresholdCheck, localCfg)

/**
 * A fake [DataCaptureEventBehavior] that returns default values.
 */
internal fun fakeDataCaptureEventBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    remoteCfg: () -> RemoteConfig? = { null }
) = DataCaptureEventBehavior(thresholdCheck, remoteCfg)

/**
 * A fake [SdkModeBehavior] that returns default values.
 */
internal fun fakeSdkModeBehavior(
    isDebug: Boolean = false,
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    localCfg: () -> LocalConfig? = { null },
    remoteCfg: () -> RemoteConfig? = { null }
) = SdkModeBehavior(isDebug, thresholdCheck, localCfg, remoteCfg)

/**
 * A fake [SdkAppBehavior] that returns default values.
 */
internal fun fakeSdkAppBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    localCfg: () -> LocalConfig? = { null },
) = SdkAppBehavior(thresholdCheck, localCfg)

/**
 * A fake [SdkModeBehavior] that returns default values.
 */
internal fun fakeSdkEndpointBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    localCfg: () -> BaseUrlLocalConfig? = { null },
) = SdkEndpointBehavior(thresholdCheck, localCfg)

/**
 * A fake [AppExitInfoBehavior] that returns default values.
 */
internal fun fakeAppExitInfoBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    localCfg: () -> AppExitInfoLocalConfig? = { null },
    remoteCfg: () -> RemoteConfig? = { null },
) = AppExitInfoBehavior(thresholdCheck, localCfg, remoteCfg)

/**
 * A fake [NetworkSpanForwardingBehavior] that returns default values.
 */
internal fun fakeNetworkSpanForwardingBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    remoteConfig: () -> NetworkSpanForwardingRemoteConfig? = { null }
) = NetworkSpanForwardingBehavior(thresholdCheck, remoteConfig)

internal fun fakeWebViewVitalsBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    remoteCfg: () -> RemoteConfig? = { null },
) = WebViewVitalsBehavior(thresholdCheck, remoteCfg)
