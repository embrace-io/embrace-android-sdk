package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.store.Ordinal
import io.embrace.android.embracesdk.internal.store.OrdinalStore

class FakeOrdinalStore : OrdinalStore {

    private val lock = Any()
    private val values = mutableMapOf<Ordinal, Int>()
    private val scopes = mutableMapOf<Ordinal, String>()

    override fun incrementAndGet(ordinal: Ordinal, scope: String?, seed: () -> Int): Int = synchronized(lock) {
        if (ordinal.scopeKey != null) {
            if (scope == null) {
                return -1
            }
            if (scopes[ordinal] != scope) {
                scopes[ordinal] = scope
                values.remove(ordinal)
            }
        }
        val next = values[ordinal]?.plus(1) ?: seed()
        values[ordinal] = next
        next
    }
}
