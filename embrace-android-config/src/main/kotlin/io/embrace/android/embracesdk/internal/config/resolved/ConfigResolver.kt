package io.embrace.android.embracesdk.internal.config.resolved

import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

fun resolveConfig(local: InstrumentedConfig, remote: RemoteConfig?, bucket: Lazy<Float>): EmbraceConfig = EmbraceConfig(
    breadcrumb = { resolveBreadcrumb(local, remote) },
    persistence = { resolvePersistence(local, remote, bucket) },
    threadBlockage = { resolveThreadBlockage(remote, bucket) },
    aei = { resolveAei(local, remote, bucket) },
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

fun resolvePersistence(local: InstrumentedConfig, remote: RemoteConfig?, bucket: Lazy<Float>): PersistenceConfig =
    PersistenceConfig(
        multiFileEnabled = {
            rolloutEnabled(remote?.pctMultiFilePersistenceEnabled, bucket)
                ?: local.enabledFeatures.isMultiFilePersistenceEnabled()
        },
    )

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
