package io.embrace.android.embracesdk.internal.prefs

import io.embrace.android.embracesdk.internal.arch.store.NetworkCaptureRuleStore
import io.embrace.android.embracesdk.internal.store.KeyValueStore

/**
 * A [NetworkCaptureRuleStore] backed by a [KeyValueStore]. This uses the same keys and encoding as the existing
 * [SharedPreferences] store.
 */
class PrefsNetworkCaptureRuleStore(
    private val store: KeyValueStore,
) : NetworkCaptureRuleStore {

    private val lock = Any()

    override fun remaining(ruleId: String, default: Int): Int = store.getInt(KEY_RULE_PREFIX + ruleId) ?: default

    override fun decrement(ruleId: String, default: Int) = synchronized(lock) {
        // clamp at 0 to avoid resetting the rule to its default
        val remaining = maxOf(remaining(ruleId, default) - 1, 0)
        store.editAndCommit { putInt(KEY_RULE_PREFIX + ruleId, remaining) }
    }

    private companion object {
        const val KEY_RULE_PREFIX = "io.embrace.networkcapturerule"
    }
}
