package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.datasource.SessionPartStateToken
import io.embrace.android.embracesdk.internal.arch.datasource.UnrecordedTransitions
import io.embrace.android.embracesdk.internal.clock.Clock

class FakeSessionPartStateToken<T>(
    val transitions: MutableList<Pair<Long, T>> = mutableListOf(),
    val transitionAttributes: MutableList<Map<String, String>> = mutableListOf(),
    private val clock: Clock = FakeClock(),
) : SessionPartStateToken<T> {
    var endTimeMs = 0L
    override fun update(
        newValue: T,
        transitionTimeMs: Long,
        transitionAttributes: Map<String, String>,
        unrecordedTransitions: UnrecordedTransitions,
    ): Boolean {
        transitions.add(Pair(transitionTimeMs, newValue))
        this.transitionAttributes.add(transitionAttributes)
        return true
    }

    override fun end(unrecordedTransitions: UnrecordedTransitions) {
        endTimeMs = clock.now()
    }
}
