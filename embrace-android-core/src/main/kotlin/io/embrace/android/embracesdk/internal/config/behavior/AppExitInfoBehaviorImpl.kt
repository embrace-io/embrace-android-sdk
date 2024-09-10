package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.local.AppExitInfoLocalConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Provides the behavior that should be followed for select services that automatically
 * capture data.
 */
class AppExitInfoBehaviorImpl(
    thresholdCheck: BehaviorThresholdCheck,
    localSupplier: Provider<AppExitInfoLocalConfig?>,
    remoteSupplier: Provider<RemoteConfig?>
) : AppExitInfoBehavior, MergedConfigBehavior<AppExitInfoLocalConfig, RemoteConfig>(
    thresholdCheck,
    localSupplier,
    remoteSupplier
) {
    companion object {
        /**
         * Max size of bytes to allow capturing AppExitInfo ndk/anr traces
         */
        private const val MAX_TRACE_SIZE_BYTES = 2097152 // 2MB
        const val AEI_MAX_NUM_DEFAULT: Int = 0 // 0 means no limit
        const val AEI_ENABLED_DEFAULT: Boolean = true
    }

    override fun getTraceMaxLimit(): Int =
        remote?.appExitInfoConfig?.appExitInfoTracesLimit
            ?: local?.appExitInfoTracesLimit
            ?: MAX_TRACE_SIZE_BYTES

    override fun isAeiCaptureEnabled(): Boolean {
        return thresholdCheck.isBehaviorEnabled(remote?.appExitInfoConfig?.pctAeiCaptureEnabled)
            ?: local?.aeiCaptureEnabled
            ?: AEI_ENABLED_DEFAULT
    }

    override fun appExitInfoMaxNum(): Int = remote?.appExitInfoConfig?.aeiMaxNum ?: AEI_MAX_NUM_DEFAULT
}
