package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.EnabledFeatureConfig
import io.embrace.android.embracesdk.internal.config.remote.AppExitInfoConfig

interface AppExitInfoBehavior : ConfigBehavior<EnabledFeatureConfig, AppExitInfoConfig> {

    fun getTraceMaxLimit(): Int

    /**
     * Whether the feature is enabled or not.
     */
    fun isAeiCaptureEnabled(): Boolean

    fun appExitInfoMaxNum(): Int

    sealed class CollectTracesResult(val result: String?) {
        class Success(result: String?) : CollectTracesResult(result)
        class TooLarge(result: String?) : CollectTracesResult(result)
        class TraceException(message: String?) : CollectTracesResult(message)
    }
}
