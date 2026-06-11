package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.store.Ordinal
import io.embrace.android.embracesdk.internal.store.OrdinalStore

class FakeOrdinalStore : OrdinalStore {

    private val lock = Any()
    private val values = mutableMapOf<Ordinal, Int>()

    override fun incrementAndGet(ordinal: Ordinal, seed: () -> Int): Int = synchronized(lock) {
        val next = values[ordinal]?.plus(1) ?: seed()
        values[ordinal] = next
        next
    }
}
