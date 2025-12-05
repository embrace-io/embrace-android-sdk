package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.datasource.StateToken

class FakeStateToken(
    val transitions: MutableList<Pair<Long, String>> = mutableListOf()
): StateToken {
    var endTimeMs = 0L
    override fun update(timestampMs: Long, newValue: String) {
        transitions.add(Pair(timestampMs, newValue))
    }

    override fun end(timestampMs: Long) {
        endTimeMs = timestampMs
    }
}
