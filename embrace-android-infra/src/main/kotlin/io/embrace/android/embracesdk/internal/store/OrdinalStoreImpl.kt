package io.embrace.android.embracesdk.internal.store

class OrdinalStoreImpl(
    private val impl: KeyValueStore,
) : OrdinalStore {

    private val lock = Any()

    /**
     * The read-modify-write is performed under [lock] and persisted in a single edit, so that seeding
     * an ordinal doesn't cost an extra commit and concurrent increments can't lose an update.
     */
    override fun incrementAndGet(ordinal: Ordinal, scope: String?, seed: () -> Int): Int = synchronized(lock) {
        val sanitized = sanitize(ordinal)
        val scopeKey = sanitized.scopeKey
        if (scopeKey != null && scope == null) {
            return -1
        }
        try {
            val newScope = when {
                scopeKey != null && impl.getString(scopeKey) != scope -> scope
                else -> null
            }
            val next = when (newScope) {
                null -> impl.getInt(sanitized.key)?.plus(1) ?: seed()
                else -> seed()
            }
            impl.editAndCommit {
                if (scopeKey != null && newScope != null) {
                    putString(scopeKey, newScope)
                }
                putInt(sanitized.key, next)
            }
            next
        } catch (tr: Throwable) {
            -1
        }
    }

    private fun sanitize(ordinal: Ordinal): Ordinal = when (ordinal) {
        Ordinal.USER_SESSION -> Ordinal.SESSION
        else -> ordinal
    }
}
