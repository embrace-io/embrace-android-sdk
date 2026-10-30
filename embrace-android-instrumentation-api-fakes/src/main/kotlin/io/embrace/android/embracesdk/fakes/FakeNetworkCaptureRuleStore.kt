package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.store.NetworkCaptureRuleStore

class FakeNetworkCaptureRuleStore : NetworkCaptureRuleStore {

    val counts: MutableMap<String, Int> = mutableMapOf()

    override fun remaining(ruleId: String, default: Int): Int = counts[ruleId] ?: default

    override fun decrement(ruleId: String, default: Int) {
        counts[ruleId] = maxOf(remaining(ruleId, default) - 1, 0)
    }
}
