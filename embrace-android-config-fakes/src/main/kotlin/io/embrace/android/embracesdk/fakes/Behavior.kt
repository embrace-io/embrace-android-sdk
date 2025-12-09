package io.embrace.android.embracesdk.fakes

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
import io.embrace.android.embracesdk.internal.config.behavior.OtelBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.SdkModeBehavior
import io.embrace.android.embracesdk.internal.config.behavior.SdkModeBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.SessionBehavior
import io.embrace.android.embracesdk.internal.config.behavior.SessionBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.ThreadBlockageBehavior
import io.embrace.android.embracesdk.internal.config.behavior.ThreadBlockageBehaviorImpl
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

const val FAKE_DEVICE_ID = "D586C4E25C064764BF53A808A38B92FE"

private val behaviorThresholdCheck = BehaviorThresholdCheck {
    FAKE_DEVICE_ID
}

/**
 * A [ThreadBlockageBehavior] that returns default values.
 */
fun createThreadBlockageBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    remoteCfg: RemoteConfig? = null,
): ThreadBlockageBehavior = ThreadBlockageBehaviorImpl(thresholdCheck, remoteCfg)

/**
 * A [SessionBehaviorImpl] that returns default values.
 */
fun createSessionBehavior(
    remoteCfg: RemoteConfig? = null,
): SessionBehavior = SessionBehaviorImpl(remoteCfg)

/**
 * A [NetworkBehaviorImpl] that returns default values.
 */
fun createNetworkBehavior(
    remoteCfg: RemoteConfig? = null,
    disabledUrlPatterns: List<String>? = null,
): NetworkBehavior = NetworkBehaviorImpl(InstrumentedConfigImpl, remoteCfg, disabledUrlPatterns)

/**
 * A [BackgroundActivityBehaviorImpl] that returns default values.
 */
fun createBackgroundActivityBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    remoteCfg: RemoteConfig? = null,
): BackgroundActivityBehavior = BackgroundActivityBehaviorImpl(thresholdCheck, InstrumentedConfigImpl, remoteCfg)

/**
 * A [AutoDataCaptureBehaviorImpl] that returns default values.
 */
fun createAutoDataCaptureBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    remoteCfg: RemoteConfig? = null,
): AutoDataCaptureBehavior = AutoDataCaptureBehaviorImpl(thresholdCheck, InstrumentedConfigImpl, remoteCfg)

/**
 * A [LogMessageBehaviorImpl] that returns default values.
 */
fun createLogMessageBehavior(
    remoteCfg: RemoteConfig? = null,
): LogMessageBehavior = LogMessageBehaviorImpl(remoteCfg)

/**
 * A [DataCaptureEventBehaviorImpl] that returns default values.
 */
fun createDataCaptureEventBehavior(
    remoteCfg: RemoteConfig? = null,
): DataCaptureEventBehavior = DataCaptureEventBehaviorImpl(remoteCfg)

/**
 * A [SdkModeBehaviorImpl] that returns default values.
 */
fun createSdkModeBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    remoteCfg: RemoteConfig? = null,
): SdkModeBehavior = SdkModeBehaviorImpl(thresholdCheck, remoteCfg)

/**
 * A [AppExitInfoBehavior] that returns default values.
 */
fun createAppExitInfoBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    remoteCfg: RemoteConfig? = null,
): AppExitInfoBehavior = AppExitInfoBehaviorImpl(thresholdCheck, InstrumentedConfigImpl, remoteCfg)

/**
 * A [NetworkSpanForwardingBehaviorImpl] that returns default values.
 */
fun createNetworkSpanForwardingBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    remoteConfig: RemoteConfig? = null,
): NetworkSpanForwardingBehavior = NetworkSpanForwardingBehaviorImpl(
    thresholdCheck,
    InstrumentedConfigImpl,
    remoteConfig
)

/**
 * A [SensitiveKeysBehaviorImpl] that returns default values.
 */
fun createSensitiveKeysBehavior() = SensitiveKeysBehaviorImpl(InstrumentedConfigImpl)

/**
 * An [OtelBehaviorImpl] that returns default values.
 */
fun createOtelBehavior(
    thresholdCheck: BehaviorThresholdCheck = behaviorThresholdCheck,
    remoteCfg: RemoteConfig? = null
) = OtelBehaviorImpl(thresholdCheck, InstrumentedConfigImpl, remoteCfg)
