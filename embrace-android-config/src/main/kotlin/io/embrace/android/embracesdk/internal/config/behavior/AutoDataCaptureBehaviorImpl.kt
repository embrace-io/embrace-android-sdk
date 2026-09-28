package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.resolved.AutoDataCaptureConfig
import io.embrace.android.embracesdk.internal.config.resolved.resolveAutoDataCapture

/**
 * Provides the behavior that should be followed for select services that automatically
 * capture data.
 */
class AutoDataCaptureBehaviorImpl(private val config: AutoDataCaptureConfig) : AutoDataCaptureBehavior {

    constructor(thresholdCheck: BehaviorThresholdCheck, local: InstrumentedConfig, remote: RemoteConfig?) :
        this(resolveAutoDataCapture(behaviorInputs(local, remote, thresholdCheck)))

    override fun isThermalStatusCaptureEnabled(): Boolean = config.thermalStatusCaptureEnabled
    override fun isPowerSaveModeCaptureEnabled(): Boolean = config.powerSaveModeCaptureEnabled
    override fun isNetworkConnectivityCaptureEnabled(): Boolean = config.networkConnectivityCaptureEnabled
    override fun isThreadBlockageCaptureEnabled(): Boolean = config.threadBlockageCaptureEnabled
    override fun isJvmCrashCaptureEnabled(): Boolean = config.jvmCrashCaptureEnabled
    override fun isComposeClickCaptureEnabled(): Boolean = config.composeClickCaptureEnabled
    override fun is3rdPartySigHandlerDetectionEnabled(): Boolean = config.thirdPartySigHandlerDetectionEnabled
    override fun isNativeCrashCaptureEnabled(): Boolean = config.nativeCrashCaptureEnabled
    override fun isDiskUsageCaptureEnabled(): Boolean = config.diskUsageCaptureEnabled
    override fun isUiLoadTracingEnabled(): Boolean = config.uiLoadTracingEnabled
    override fun isUiLoadTracingTraceAll(): Boolean = config.uiLoadTracingTraceAll
    override fun isEndStartupWithAppReadyEnabled(): Boolean = config.endStartupWithAppReadyEnabled
    override fun isStateCaptureEnabled(): Boolean = config.stateCaptureEnabled
    override fun isNetworkCallbackConnectivityServiceEnabled(): Boolean = config.networkCallbackConnectivityServiceEnabled
    override fun isNavigationStateCaptureEnabled(): Boolean = config.navigationStateCaptureEnabled
    override fun isSmoothnessCaptureEnabled(): Boolean = config.smoothnessCaptureEnabled
    override fun isScreenLoadCaptureEnabled(): Boolean = config.screenLoadCaptureEnabled
    override fun isActivityProcessLifecycleTrackerEnabled(): Boolean = config.activityProcessLifecycleTrackerEnabled
    override fun isActivityLeakDetectionEnabled(): Boolean = config.activityLeakDetectionEnabled
    override fun isFragmentLeakDetectionEnabled(): Boolean = config.fragmentLeakDetectionEnabled
    override fun isWebViewLeakDetectionEnabled(): Boolean = config.webViewLeakDetectionEnabled
}
