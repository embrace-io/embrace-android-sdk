package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.config.local.AppExitInfoLocalConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Provides the behavior that should be followed for select services that automatically
 * capture data.
 */
internal class AppExitInfoBehavior(
    thresholdCheck: BehaviorThresholdCheck,
    localSupplier: Provider<AppExitInfoLocalConfig?>,
    remoteSupplier: Provider<RemoteConfig?>
) : MergedConfigBehavior<AppExitInfoLocalConfig, RemoteConfig>(
    thresholdCheck,
    localSupplier,
    remoteSupplier
) {
    companion object {
        /**
         * Max size of bytes to allow capturing AppExitInfo ndk/anr traces
         */
        private const val MAX_TRACE_SIZE_BYTES = 2097152 // 2MB
        const val AEI_MAX_NUM_DEFAULT = 0 // 0 means no limit
        const val AEI_ENABLED_DEFAULT = true
    }

    sealed class CollectTracesResult(val result: String?) {
        class Success(result: String?) : CollectTracesResult(result)
        class TooLarge(result: String?) : CollectTracesResult(result)
        class TraceException(message: String?) : CollectTracesResult(message)
    }

    fun getTraceMaxLimit(): Int =
        remote?.appExitInfoConfig?.appExitInfoTracesLimit
            ?: local?.appExitInfoTracesLimit
            ?: MAX_TRACE_SIZE_BYTES

    /**
     * Whether the feature is enabled or not.
     */
    fun isEnabled(): Boolean {
        return thresholdCheck.isBehaviorEnabled(remote?.appExitInfoConfig?.pctAeiCaptureEnabled)
            ?: local?.aeiCaptureEnabled
            ?: AEI_ENABLED_DEFAULT
    }

    fun appExitInfoMaxNum() = remote?.appExitInfoConfig?.aeiMaxNum ?: AEI_MAX_NUM_DEFAULT
}
