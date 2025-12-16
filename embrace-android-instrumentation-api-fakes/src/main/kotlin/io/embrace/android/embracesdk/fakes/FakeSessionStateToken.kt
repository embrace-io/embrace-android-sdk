package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.datasource.SessionStateToken
import io.embrace.android.embracesdk.internal.arch.datasource.UnrecordedTransitions
import io.embrace.android.embracesdk.internal.clock.Clock

class FakeSessionStateToken<T>(
    val transitions: MutableList<Pair<Long, T>> = mutableListOf(),
    private val clock: Clock = FakeClock(),
) : SessionStateToken<T> {
    var endTimeMs = 0L
    override fun update(updateDetectedTimeMs: Long, newValue: T, unrecordedTransitions: UnrecordedTransitions): Boolean {
        transitions.add(Pair(updateDetectedTimeMs, newValue))
        return true
    }

    override fun end(unrecordedTransitions: UnrecordedTransitions) {
        endTimeMs = clock.now()
    }
}
