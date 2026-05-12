package io.embrace.android.embracesdk.internal.store

class OrdinalStoreImpl(
    private val impl: KeyValueStore,
) : OrdinalStore {

    override fun incrementAndGet(ordinal: Ordinal, seed: () -> Int): Int {
        val key = sanitize(ordinal).key
        seedIfAbsent(key, seed)
        return impl.incrementAndGet(key)
    }

    private fun seedIfAbsent(key: String, seed: () -> Int) {
        if (impl.getInt(key) == null) {
            impl.edit { putInt(key, seed() - 1) }
        }
    }

    private fun sanitize(ordinal: Ordinal): Ordinal = when (ordinal) {
        Ordinal.USER_SESSION -> Ordinal.SESSION
        else -> ordinal
    }
}
