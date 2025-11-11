package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.store.Ordinal
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import java.util.concurrent.atomic.AtomicInteger

class FakeOrdinalStore : OrdinalStore {

    private val counts = Ordinal.entries.associateWith { AtomicInteger(0) }

    override fun incrementAndGet(ordinal: Ordinal): Int {
        return checkNotNull(counts[ordinal]).incrementAndGet()
    }
}
