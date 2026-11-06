package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.store.Ordinal
import io.embrace.android.embracesdk.internal.store.OrdinalStore

/**
 * Stands in when `ordinals.bin` can't be opened, returning -1 as a failed increment does.
 */
internal object UnavailableOrdinalStore : OrdinalStore {
    override fun incrementAndGet(ordinal: Ordinal, scope: String?, seed: () -> Int): Int = -1
}
