package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.config.remote.SpansRemoteConfig

internal class SpansBehavior(
    thresholdCheck: BehaviorThresholdCheck,
    remoteSupplier: () -> SpansRemoteConfig?
) : MergedConfigBehavior<UnimplementedConfig, SpansRemoteConfig>(
    thresholdCheck,
    { null },
    remoteSupplier
) {
    companion object {
        private const val DEFAULT_PCT_ENABLED = 100.0f
    }

    fun isSpansEnabled(): Boolean {
        return thresholdCheck.isBehaviorEnabled(remote?.pctEnabled ?: DEFAULT_PCT_ENABLED)
    }
}
