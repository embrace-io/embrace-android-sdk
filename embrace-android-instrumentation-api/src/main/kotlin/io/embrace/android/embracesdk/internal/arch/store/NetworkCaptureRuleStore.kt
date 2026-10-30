package io.embrace.android.embracesdk.internal.arch.store

/**
 * Persists how many more times each network capture rule can be applied.
 */
interface NetworkCaptureRuleStore {

    /**
     * Returns the remaining count for the rule, or [default] if none has been stored.
     */
    fun remaining(ruleId: String, default: Int): Int

    /**
     * Decrements the remaining count for the rule, starting from [default] if none has been stored.
     */
    fun decrement(ruleId: String, default: Int)
}
