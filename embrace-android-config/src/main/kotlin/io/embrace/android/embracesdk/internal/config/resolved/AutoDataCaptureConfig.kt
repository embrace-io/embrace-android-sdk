package io.embrace.android.embracesdk.internal.config.resolved

/**
 * Resolved config for features that automatically capture data.
 */
interface AutoDataCaptureConfig {

    /**
     * Whether the SDK should automatically capture thermal status data.
     */
    val thermalStatusCaptureEnabled: Boolean

    /**
     * Whether power save mode data should be captured automatically.
     */
    val powerSaveModeCaptureEnabled: Boolean

    /**
     * Whether network connectivity data should be captured automatically.
     */
    val networkConnectivityCaptureEnabled: Boolean

    /**
     * Whether thread blockages should be captured.
     */
    val threadBlockageCaptureEnabled: Boolean

    /**
     * Whether the SDK automatically attaches to the uncaught exception handler.
     */
    val jvmCrashCaptureEnabled: Boolean

    /**
     * Whether Jetpack Compose click events should be captured.
     */
    val composeClickCaptureEnabled: Boolean

    /**
     * Whether the SDK should attempt to overwrite other signal handlers.
     */
    val thirdPartySigHandlerDetectionEnabled: Boolean

    /**
     * Whether NDK error capture is enabled.
     */
    val nativeCrashCaptureEnabled: Boolean

    /**
     * Whether app disk usage is scanned and reported. This can be a costly operation for apps with a lot of
     * local files.
     */
    val diskUsageCaptureEnabled: Boolean

    /**
     * Whether traces are captured for the performance of the opening of Activities.
     */
    val uiLoadTracingEnabled: Boolean

    /**
     * Whether traces are captured for the performance of the opening of all Activities by default.
     */
    val uiLoadTracingTraceAll: Boolean

    /**
     * Whether the app startup trace waits for a call to Embrace.appReady() to signal completion.
     */
    val endStartupWithAppReadyEnabled: Boolean

    /**
     * Whether the state-as-span feature is enabled.
     */
    val stateCaptureEnabled: Boolean

    /**
     * Whether the NetworkCallback-based connectivity service implementation is enabled.
     */
    val networkCallbackConnectivityServiceEnabled: Boolean

    /**
     * Whether navigation state capture is enabled.
     */
    val navigationStateCaptureEnabled: Boolean

    /**
     * Whether smoothness vital capture is enabled.
     */
    val smoothnessCaptureEnabled: Boolean

    /**
     * Whether screen-load vital capture is enabled.
     */
    val screenLoadCaptureEnabled: Boolean

    /**
     * Whether the activity-callback-based process lifecycle tracker should be used instead of the
     * androidx ProcessLifecycleOwner implementation.
     */
    val activityProcessLifecycleTrackerEnabled: Boolean

    /**
     * Whether Activity leak detection is enabled.
     */
    val activityLeakDetectionEnabled: Boolean

    /**
     * Whether Fragment and Fragment View leak detection is enabled.
     */
    val fragmentLeakDetectionEnabled: Boolean

    /**
     * Whether WebView leak detection is enabled.
     */
    val webViewLeakDetectionEnabled: Boolean
}

/**
 * Creates an [AutoDataCaptureConfig]. A provider returning null means the default is used.
 */
@Suppress("LongParameterList", "CyclomaticComplexMethod")
inline fun AutoDataCaptureConfig(
    crossinline thermalStatusCaptureEnabled: () -> Boolean? = { null },
    crossinline powerSaveModeCaptureEnabled: () -> Boolean? = { null },
    crossinline networkConnectivityCaptureEnabled: () -> Boolean? = { null },
    crossinline threadBlockageCaptureEnabled: () -> Boolean? = { null },
    crossinline jvmCrashCaptureEnabled: () -> Boolean? = { null },
    crossinline composeClickCaptureEnabled: () -> Boolean? = { null },
    crossinline thirdPartySigHandlerDetectionEnabled: () -> Boolean? = { null },
    crossinline nativeCrashCaptureEnabled: () -> Boolean? = { null },
    crossinline diskUsageCaptureEnabled: () -> Boolean? = { null },
    crossinline uiLoadTracingEnabled: () -> Boolean? = { null },
    crossinline uiLoadTracingTraceAll: () -> Boolean? = { null },
    crossinline endStartupWithAppReadyEnabled: () -> Boolean? = { null },
    crossinline stateCaptureEnabled: () -> Boolean? = { null },
    crossinline networkCallbackConnectivityServiceEnabled: () -> Boolean? = { null },
    crossinline navigationStateCaptureEnabled: () -> Boolean? = { null },
    crossinline smoothnessCaptureEnabled: () -> Boolean? = { null },
    crossinline screenLoadCaptureEnabled: () -> Boolean? = { null },
    crossinline activityProcessLifecycleTrackerEnabled: () -> Boolean? = { null },
    crossinline activityLeakDetectionEnabled: () -> Boolean? = { null },
    crossinline fragmentLeakDetectionEnabled: () -> Boolean? = { null },
    crossinline webViewLeakDetectionEnabled: () -> Boolean? = { null },
): AutoDataCaptureConfig = object : AutoDataCaptureConfig {
    override val thermalStatusCaptureEnabled: Boolean = thermalStatusCaptureEnabled() ?: true
    override val powerSaveModeCaptureEnabled: Boolean = powerSaveModeCaptureEnabled() ?: true
    override val networkConnectivityCaptureEnabled: Boolean = networkConnectivityCaptureEnabled() ?: true
    override val threadBlockageCaptureEnabled: Boolean = threadBlockageCaptureEnabled() ?: true
    override val jvmCrashCaptureEnabled: Boolean = jvmCrashCaptureEnabled() ?: true
    override val composeClickCaptureEnabled: Boolean = composeClickCaptureEnabled() ?: false
    override val thirdPartySigHandlerDetectionEnabled: Boolean = thirdPartySigHandlerDetectionEnabled() ?: false
    override val nativeCrashCaptureEnabled: Boolean = nativeCrashCaptureEnabled() ?: true
    override val diskUsageCaptureEnabled: Boolean = diskUsageCaptureEnabled() ?: true
    override val uiLoadTracingEnabled: Boolean = uiLoadTracingEnabled() ?: true
    override val uiLoadTracingTraceAll: Boolean = uiLoadTracingTraceAll() ?: true
    override val endStartupWithAppReadyEnabled: Boolean = endStartupWithAppReadyEnabled() ?: false
    override val stateCaptureEnabled: Boolean = stateCaptureEnabled() ?: true
    override val networkCallbackConnectivityServiceEnabled: Boolean = networkCallbackConnectivityServiceEnabled() ?: false
    override val navigationStateCaptureEnabled: Boolean = navigationStateCaptureEnabled() ?: true
    override val smoothnessCaptureEnabled: Boolean = smoothnessCaptureEnabled() ?: false
    override val screenLoadCaptureEnabled: Boolean = screenLoadCaptureEnabled() ?: false
    override val activityProcessLifecycleTrackerEnabled: Boolean = activityProcessLifecycleTrackerEnabled() ?: false
    override val activityLeakDetectionEnabled: Boolean = activityLeakDetectionEnabled() ?: false
    override val fragmentLeakDetectionEnabled: Boolean = fragmentLeakDetectionEnabled() ?: false
    override val webViewLeakDetectionEnabled: Boolean = webViewLeakDetectionEnabled() ?: false
}
