package io.embrace.android.embracesdk.internal.store

class OrdinalStoreImpl(
    private val impl: KeyValueStore,
) : OrdinalStore {

    override fun incrementAndGet(ordinal: Ordinal): Int {
        return impl.incrementAndGet(ordinal.key)
    }
}
