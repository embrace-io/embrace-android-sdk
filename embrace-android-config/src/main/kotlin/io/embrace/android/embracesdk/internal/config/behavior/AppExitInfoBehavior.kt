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
}
