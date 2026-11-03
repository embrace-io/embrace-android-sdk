package io.embrace.android.embracesdk.internal.prefs

import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.StoreWriteBatcher

/**
 * A [StoreWriteBatcher] that folds the writes into a single [KeyValueStore] commit.
 */
class PrefsStoreWriteBatcher(
    private val store: KeyValueStore,
) : StoreWriteBatcher {
    override fun batch(action: () -> Unit) = store.batch(action)
}
