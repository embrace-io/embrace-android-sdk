package io.embrace.android.embracesdk.internal.prefs

import io.embrace.android.embracesdk.internal.arch.store.DeliveredAeiStore
import io.embrace.android.embracesdk.internal.store.KeyValueStore

/**
 * A [DeliveredAeiStore] backed by a [KeyValueStore]. This uses the same key and encoding as the existing
 * [SharedPreferences] store.
 */
class PrefsDeliveredAeiStore(
    private val store: KeyValueStore,
) : DeliveredAeiStore {

    private val lock = Any()

    override fun ids(): Set<String> = store.getStringSet(KEY_AEI_IDS) ?: emptySet()

    override fun add(id: String) = synchronized(lock) {
        write(ids().plus(id))
    }

    override fun retainOnly(ids: Set<String>) = synchronized(lock) {
        write(this.ids().intersect(ids))
    }

    private fun write(ids: Set<String>) {
        store.editAndCommit { putStringSet(KEY_AEI_IDS, ids) }
    }

    private companion object {
        const val KEY_AEI_IDS = "io.embrace.aeiHashCode"
    }
}
