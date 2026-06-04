package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.EnabledFeatureConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.NetworkCaptureConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

class TraceparentInjectionBehaviorImpl(
    private val thresholdCheck: BehaviorThresholdCheck,
    local: InstrumentedConfig,
    remote: RemoteConfig?,
) : TraceparentInjectionBehavior {

    private val enabledFeatures: EnabledFeatureConfig = local.enabledFeatures
    private val networkCapture: NetworkCaptureConfig = local.networkCapture
    private val injectionPctEnabled: Float? = remote?.traceparentInjectionPctEnabled
    private val allowlistMatcher = HostAllowlistMatcher(networkCapture.getTraceparentOnlyAllowDomains())

    override fun isTraceparentInjectionEnabled(): Boolean {
        return injectionPctEnabled?.let { thresholdCheck.isBehaviorEnabled(it) }
            ?: enabledFeatures.isTraceparentInjectionEnabled()
    }

    override fun shouldInjectTraceparent(host: String?): Boolean = isTraceparentInjectionEnabled() && allowlistMatcher.isAllowed(host)

    /**
     * Case-insensitively matches a request host against the local allowlist of hostnames. If the allowList is not provided at init time,
     * all hosts will return as allowed.
     */
    private class HostAllowlistMatcher(
        allowlist: List<String>?,
    ) {
        private val entries: List<String>? = allowlist?.map { it.lowercase() }

        fun isAllowed(host: String?): Boolean {
            val entries = entries ?: return true

            if (entries.isEmpty() || host.isNullOrEmpty()) {
                return false
            }

            val normalizedHost = host.lowercase()
            return entries.any { entry ->
                normalizedHost == entry || (entry.startsWith(".") && normalizedHost.endsWith(entry))
            }
        }
    }
}
