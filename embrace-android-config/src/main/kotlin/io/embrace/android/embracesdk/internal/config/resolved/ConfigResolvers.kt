package io.embrace.android.embracesdk.internal.config.resolved

import kotlin.math.min

// Resolvers for the options that config-schema/embrace-config.yaml can't express declaratively. Each is named by
// an option's `android.resolver`, and returning null means the option's default is used.

/**
 * UI load tracing is configured locally, but can be turned off remotely.
 */
fun resolveUiLoadTracingEnabled(inputs: ConfigInputs): Boolean = with(inputs) {
    local.enabledFeatures.isUiLoadTracingEnabled() && remote?.uiLoadInstrumentationEnabled ?: true
}

/**
 * Tracing every Activity is configured locally, but UI load tracing can be turned off remotely.
 */
fun resolveUiLoadTracingTraceAll(inputs: ConfigInputs): Boolean = with(inputs) {
    local.enabledFeatures.isUiLoadTracingTraceAll() && remote?.uiLoadInstrumentationEnabled ?: true
}

/**
 * HUC Lite is off whenever full HttpUrlConnection capture is on.
 */
fun resolveHucLiteInstrumentationEnabled(inputs: ConfigInputs): Boolean = with(inputs.local.enabledFeatures) {
    isHucLiteInstrumentationEnabled() && !isHttpUrlConnectionCaptureEnabled()
}

/**
 * The local limit is a ceiling on the remote one.
 */
fun resolveRequestLimitPerDomain(inputs: ConfigInputs): Int = with(inputs) {
    min(
        remote?.networkConfig?.defaultCaptureLimit ?: NetworkConfig.DEFAULT_REQUEST_LIMIT_PER_DOMAIN,
        local.networkCapture.getRequestLimitPerDomain(),
    )
}

/**
 * The remote limits replace the local ones, and every limit is capped at the default per-domain limit.
 */
fun resolveLimitsByDomain(inputs: ConfigInputs): Map<String, Int> = with(inputs) {
    val limits = remote?.networkConfig?.domainLimits
        ?: local.networkCapture.getLimitsByDomain().mapValues { it.value.toInt() }
    val ceiling = resolveRequestLimitPerDomain(inputs)
    limits.mapValues { min(it.value, ceiling) }
}

/**
 * The local patterns are a list, so they are converted to match the remote set.
 */
fun resolveDisabledUrlPatterns(inputs: ConfigInputs): Set<String> = with(inputs) {
    remote?.disabledUrlPatterns ?: local.networkCapture.getIgnoredRequestPatternList().toSet()
}

/**
 * The remote percentage falls back to the deprecated network_span_forwarding.pct_enabled.
 */
fun resolveNetworkSpanForwardingEnabled(inputs: ConfigInputs): Boolean = with(inputs) {
    @Suppress("DEPRECATION")
    val pct = remote?.let { it.nsfPctEnabled ?: it.networkSpanForwardingRemoteConfig?.pctEnabled }
    rolloutEnabled(pct, bucket) ?: local.enabledFeatures.isNetworkSpanForwardingEnabled()
}

/**
 * Injection is enabled outright by the legacy fallback, and otherwise follows the usual precedence.
 */
fun resolveTraceparentInjectionEnabled(inputs: ConfigInputs): Boolean = with(inputs) {
    resolveTraceparentLegacyFallbackEnabled(inputs) ||
        rolloutEnabled(remote?.traceparentInjectionPctEnabled, bucket) ?: local.enabledFeatures.isTraceparentInjectionEnabled()
}

/**
 * The deprecated network_span_forwarding.pct_enabled enables injection for every host, but only for configs that
 * predate nsf_pct_enabled.
 */
fun resolveTraceparentLegacyFallbackEnabled(inputs: ConfigInputs): Boolean = with(inputs) {
    val remote = remote ?: return false
    if (remote.nsfPctEnabled != null) {
        return false
    }
    @Suppress("DEPRECATION")
    rolloutEnabled(remote.networkSpanForwardingRemoteConfig?.pctEnabled, bucket) == true
}

/**
 * A remote timeout is ignored if it is outside its range or longer than the maximum session duration.
 */
fun resolveInactivityTimeoutSeconds(inputs: ConfigInputs): Int? = with(UserSessionConfig) {
    val session = inputs.remote?.userSession
    val maxDuration = session?.maxDurationSeconds?.takeIf { it in MAX_DURATION_SECONDS_MIN..MAX_DURATION_SECONDS_MAX }
        ?: DEFAULT_MAX_DURATION_SECONDS
    session?.inactivityTimeoutSeconds
        ?.takeIf { it in INACTIVITY_TIMEOUT_SECONDS_MIN..INACTIVITY_TIMEOUT_SECONDS_MAX && it <= maxDuration }
}
