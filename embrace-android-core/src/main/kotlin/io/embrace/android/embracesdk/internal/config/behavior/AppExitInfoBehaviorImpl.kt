package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

/**
 * Provides the behavior that should be followed for select services that automatically
 * capture data.
 */
class AppExitInfoBehaviorImpl(
    private val thresholdCheck: BehaviorThresholdCheck,
    local: InstrumentedConfig,
    remote: RemoteConfig?,
) : AppExitInfoBehavior {

    companion object {
        /**
         * Max size of bytes to allow capturing AppExitInfo ndk/anr traces
         */
        private const val MAX_TRACE_SIZE_BYTES = 2097152 // 2MB
        const val AEI_MAX_NUM_DEFAULT: Int = 0 // 0 means no limit
    }

    override val local = local.enabledFeatures
    override val remote = remote?.appExitInfoConfig

    override fun getTraceMaxLimit(): Int =
        remote?.appExitInfoTracesLimit
            ?: MAX_TRACE_SIZE_BYTES

    override fun isAeiCaptureEnabled(): Boolean {
        return thresholdCheck.isBehaviorEnabled(remote?.pctAeiCaptureEnabled)
            ?: local.isAeiCaptureEnabled()
    }

    override fun appExitInfoMaxNum(): Int = remote?.aeiMaxNum ?: AEI_MAX_NUM_DEFAULT
}
