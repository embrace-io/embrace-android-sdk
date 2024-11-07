package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.UnimplementedConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Provides the behavior that should be followed for select services that automatically
 * capture data.
 */
class AppExitInfoBehaviorImpl(
    thresholdCheck: BehaviorThresholdCheck,
    remoteSupplier: Provider<RemoteConfig?>,
    private val instrumentedConfig: InstrumentedConfig,
) : AppExitInfoBehavior, MergedConfigBehavior<UnimplementedConfig, RemoteConfig>(
    thresholdCheck = thresholdCheck,
    remoteSupplier = remoteSupplier
) {
    companion object {
        /**
         * Max size of bytes to allow capturing AppExitInfo ndk/anr traces
         */
        private const val MAX_TRACE_SIZE_BYTES = 2097152 // 2MB
        const val AEI_MAX_NUM_DEFAULT: Int = 0 // 0 means no limit
    }

    override fun getTraceMaxLimit(): Int =
        remote?.appExitInfoConfig?.appExitInfoTracesLimit
            ?: MAX_TRACE_SIZE_BYTES

    override fun isAeiCaptureEnabled(): Boolean {
        return thresholdCheck.isBehaviorEnabled(remote?.appExitInfoConfig?.pctAeiCaptureEnabled)
            ?: instrumentedConfig.enabledFeatures.isAeiCaptureEnabled()
    }

    override fun appExitInfoMaxNum(): Int = remote?.appExitInfoConfig?.aeiMaxNum ?: AEI_MAX_NUM_DEFAULT
}
