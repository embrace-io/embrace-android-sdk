package io.embrace.android.embracesdk.internal.config.behavior

interface AppExitInfoBehavior {

    fun getTraceMaxLimit(): Int

    /**
     * Whether the feature is enabled or not.
     */
    fun isAeiCaptureEnabled(): Boolean

    fun appExitInfoMaxNum(): Int
}
