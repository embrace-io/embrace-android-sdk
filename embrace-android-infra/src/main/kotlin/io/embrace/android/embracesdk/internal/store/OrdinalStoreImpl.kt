package io.embrace.android.embracesdk.internal.store

class OrdinalStoreImpl(
    private val impl: KeyValueStore,
) : OrdinalStore {

    override fun incrementAndGet(ordinal: Ordinal, scope: String?, seed: () -> Int): Int {
        val sanitized = sanitize(ordinal)
        val scopeKey = sanitized.scopeKey
        if (scopeKey == null) {
            seedIfAbsent(sanitized.key, seed)
        } else {
            if (scope == null) {
                return -1
            }
            if (impl.getString(scopeKey) != scope) {
                impl.edit {
                    putString(scopeKey, scope)
                    putInt(sanitized.key, seed() - 1)
                }
            }
        }
        return impl.incrementAndGet(sanitized.key)
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
