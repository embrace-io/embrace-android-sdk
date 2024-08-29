package io.embrace.android.embracesdk.internal.config.behavior

public interface AppExitInfoBehavior {

    public fun getTraceMaxLimit(): Int

    /**
     * Whether the feature is enabled or not.
     */
    public fun isEnabled(): Boolean

    public fun appExitInfoMaxNum(): Int

    public sealed class CollectTracesResult(public val result: String?) {
        public class Success(result: String?) : CollectTracesResult(result)
        public class TooLarge(result: String?) : CollectTracesResult(result)
        public class TraceException(message: String?) : CollectTracesResult(message)
    }
}
