package io.embrace.android.embracesdk.internal.store

class OrdinalStoreImpl(
    private val impl: KeyValueStore,
) : OrdinalStore {

    override fun incrementAndGet(ordinal: Ordinal): Int {
        val sanitizedOrdinal = when (ordinal) {
            Ordinal.USER_SESSION -> Ordinal.SESSION
            else -> ordinal
        }
        return impl.incrementAndGet(sanitizedOrdinal.key)
    }
}
