package io.embrace.android.embracesdk.internal.config.behavior

interface AppExitInfoBehavior {

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
