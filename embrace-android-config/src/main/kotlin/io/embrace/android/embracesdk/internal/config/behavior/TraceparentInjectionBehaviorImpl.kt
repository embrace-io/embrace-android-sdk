package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.resolved.TraceparentInjectionConfig
import io.embrace.android.embracesdk.internal.config.resolved.resolveTraceparentInjection

class TraceparentInjectionBehaviorImpl(private val config: TraceparentInjectionConfig) : TraceparentInjectionBehavior {

    constructor(thresholdCheck: BehaviorThresholdCheck, local: InstrumentedConfig, remote: RemoteConfig?) :
        this(resolveTraceparentInjection(behaviorInputs(local, remote, thresholdCheck)))

    private val allowlistMatcher = HostAllowlistMatcher(config.onlyAllowDomains)

    override fun isTraceparentInjectionEnabled(): Boolean = config.enabled

    override fun shouldInjectTraceparent(host: String?): Boolean =
        config.enabled && (config.legacyFallbackEnabled || allowlistMatcher.isAllowed(host))

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
