package io.embrace.android.embracesdk.internal.config.resolved

import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

fun resolveConfig(local: InstrumentedConfig, remote: RemoteConfig?, bucket: Lazy<Float>): EmbraceConfig = EmbraceConfig(
    breadcrumb = { resolveBreadcrumb(local, remote) },
    persistence = { resolvePersistence(local, remote, bucket) },
    threadBlockage = { resolveThreadBlockage(remote, bucket) },
    aei = { resolveAei(local, remote, bucket) },
    log = { resolveLog(remote) },
    experiment = { resolveExperiment(remote) },
    vitals = { resolveVitals(remote, bucket) },
    sdkMode = { resolveSdkMode(remote, bucket) },
    backgroundActivity = { resolveBackgroundActivity(local, remote, bucket) },
    autoDataCapture = { resolveAutoDataCapture(local, remote, bucket) },
    dataCaptureEvent = { resolveDataCaptureEvent(remote) },
    sensitiveKeys = { resolveSensitiveKeys(local) },
)

fun resolveBreadcrumb(local: InstrumentedConfig, remote: RemoteConfig?): BreadcrumbConfig {
    val features = local.enabledFeatures
    val ui = remote?.uiConfig
    return BreadcrumbConfig(
        customLimit = { ui?.breadcrumbs },
        fragmentLimit = { ui?.fragments },
        tapLimit = { ui?.taps },
        webViewLimit = { ui?.webViews },
        captureViewClickCoordinates = features::isViewClickCoordinateCaptureEnabled,
        captureActivities = features::isActivityBreadcrumbCaptureEnabled,
        captureWebViews = features::isWebViewBreadcrumbCaptureEnabled,
        captureWebViewQueryParams = features::isWebViewBreadcrumbQueryParamCaptureEnabled,
        webViewFragmentCapture = features::getWebViewBreadcrumbFragmentCapture,
        captureFcmPiiData = features::isFcmPiiDataCaptureEnabled,
    )
}

fun resolveThreadBlockage(remote: RemoteConfig?, bucket: Lazy<Float>): ThreadBlockageConfig {
    val cfg = remote?.threadBlockageRemoteConfig
    return ThreadBlockageConfig(
        captureEnabled = { rolloutEnabled(cfg?.pctEnabled?.toFloat(), bucket) },
        sampleIntervalMs = { cfg?.sampleIntervalMs },
        maxStacktracesPerInterval = { cfg?.maxStacktracesPerInterval },
        stacktraceFrameLimit = { cfg?.stacktraceFrameLimit },
        maxIntervalsPerSession = { cfg?.intervalsPerSession },
        minDurationMs = { cfg?.minDuration },
    )
}

fun resolveAei(local: InstrumentedConfig, remote: RemoteConfig?, bucket: Lazy<Float>): AeiConfig {
    val cfg = remote?.appExitInfoConfig
    return AeiConfig(
        captureEnabled = {
            rolloutEnabled(cfg?.pctAeiCaptureEnabled, bucket) ?: local.enabledFeatures.isAeiCaptureEnabled()
        },
        traceMaxLimit = { cfg?.appExitInfoTracesLimit },
        maxNum = { cfg?.aeiMaxNum },
    )
}

fun resolveLog(remote: RemoteConfig?): LogConfig {
    val cfg = remote?.logConfig
    return LogConfig(
        maxMessageLength = { cfg?.logMessageMaximumAllowedLength },
        infoLimit = { cfg?.logInfoLimit },
        warnLimit = { cfg?.logWarnLimit },
        errorLimit = { cfg?.logErrorLimit },
    )
}

fun resolveExperiment(remote: RemoteConfig?): ExperimentConfig = ExperimentConfig(
    maxCount = { remote?.experimentMaxCount },
    maxIdLength = { remote?.experimentIdMaxLength },
    maxVariantLength = { remote?.experimentVariantMaxLength },
)

fun resolveVitals(remote: RemoteConfig?, bucket: Lazy<Float>): VitalsConfig {
    val cfg = remote?.vitalsRemoteConfig
    return VitalsConfig(
        smoothnessIdleThresholdMs = { cfg?.smoothnessIdleThresholdMs },
        smoothnessHeldIdleThresholdMs = { cfg?.smoothnessHeldIdleThresholdMs },
        jankHeuristicMultiplier = { cfg?.jankHeuristicMultiplier },
        screenLoadIdleThresholdMs = { cfg?.screenLoadIdleThresholdMs },
        screenLoadTimeoutMs = { cfg?.screenLoadTimeoutMs },
        screenLoadNavTimeoutMs = { cfg?.screenLoadNavTimeoutMs },
        smoothnessFrameTraceEnabled = { rolloutEnabled(cfg?.smoothnessFrameTracePctEnabled, bucket) },
        spanLimit = { cfg?.spanLimit },
    )
}

fun resolveSdkMode(remote: RemoteConfig?, bucket: Lazy<Float>): SdkModeConfig = SdkModeConfig(
    sdkDisabled = { rolloutEnabled(remote?.threshold?.toFloat(), bucket)?.not() },
)

fun resolveBackgroundActivity(
    local: InstrumentedConfig,
    remote: RemoteConfig?,
    bucket: Lazy<Float>,
): BackgroundActivityConfig = BackgroundActivityConfig(
    captureEnabled = {
        rolloutEnabled(remote?.backgroundActivityConfig?.threshold, bucket)
            ?: local.enabledFeatures.isBackgroundActivityCaptureEnabled()
    },
)

fun resolveAutoDataCapture(
    local: InstrumentedConfig,
    remote: RemoteConfig?,
    bucket: Lazy<Float>,
): AutoDataCaptureConfig {
    val features = local.enabledFeatures
    val killSwitch = remote?.killSwitchConfig
    val uiLoadEnabledRemotely = remote?.uiLoadInstrumentationEnabled ?: true
    return AutoDataCaptureConfig(
        thermalStatusCaptureEnabled = { rolloutEnabled(remote?.dataConfig?.pctThermalStatusEnabled, bucket) },
        powerSaveModeCaptureEnabled = features::isPowerSaveModeCaptureEnabled,
        networkConnectivityCaptureEnabled = features::isNetworkConnectivityCaptureEnabled,
        threadBlockageCaptureEnabled = features::isThreadBlockageCaptureEnabled,
        jvmCrashCaptureEnabled = features::isJvmCrashCaptureEnabled,
        composeClickCaptureEnabled = { killSwitch?.jetpackCompose ?: features.isComposeClickCaptureEnabled() },
        thirdPartySigHandlerDetectionEnabled = {
            killSwitch?.sigHandlerDetection ?: features.is3rdPartySigHandlerDetectionEnabled()
        },
        nativeCrashCaptureEnabled = features::isNativeCrashCaptureEnabled,
        diskUsageCaptureEnabled = features::isDiskUsageCaptureEnabled,
        uiLoadTracingEnabled = { features.isUiLoadTracingEnabled() && uiLoadEnabledRemotely },
        uiLoadTracingTraceAll = { features.isUiLoadTracingTraceAll() && uiLoadEnabledRemotely },
        endStartupWithAppReadyEnabled = features::isEndStartupWithAppReadyEnabled,
        stateCaptureEnabled = { rolloutEnabled(remote?.pctStateCaptureEnabledV2, bucket) },
        networkCallbackConnectivityServiceEnabled = {
            rolloutEnabled(remote?.pctNetworkCallbackConnectivityServiceEnabled, bucket)
        },
        navigationStateCaptureEnabled = { rolloutEnabled(remote?.pctNavigationStateCaptureEnabled, bucket) },
        smoothnessCaptureEnabled = { rolloutEnabled(remote?.pctSmoothnessEnabled, bucket) },
        screenLoadCaptureEnabled = { rolloutEnabled(remote?.pctScreenLoadEnabled, bucket) },
        activityProcessLifecycleTrackerEnabled = {
            rolloutEnabled(remote?.pctActivityProcessLifecycleTrackerEnabled, bucket)
                ?: features.isActivityProcessLifecycleTrackerEnabled()
        },
        activityLeakDetectionEnabled = { rolloutEnabled(remote?.pctActivityLeakDetectionEnabled, bucket) },
        fragmentLeakDetectionEnabled = { rolloutEnabled(remote?.pctFragmentLeakDetectionEnabled, bucket) },
        webViewLeakDetectionEnabled = { rolloutEnabled(remote?.pctWebViewLeakDetectionEnabled, bucket) },
    )
}

fun resolveDataCaptureEvent(remote: RemoteConfig?): DataCaptureEventConfig = DataCaptureEventConfig(
    internalExceptionCaptureEnabled = { remote?.internalExceptionCaptureEnabled },
    disabledEventAndLogPatterns = { remote?.disabledEventAndLogPatterns },
)

fun resolveSensitiveKeys(local: InstrumentedConfig): SensitiveKeysConfig = SensitiveKeysConfig(
    denylist = local.redaction::getSensitiveKeysDenylist,
)
