package io.embrace.android.embracesdk.internal.store

interface OrdinalStore {

    /**
     * Increments and returns the ordinal. An ordinal that has never been written returns the value
     * supplied by [seed] on its first read; subsequent calls increment by 1. [seed] returns 1 by default.
     *
     * For an ordinal that declares a [Ordinal.scopeKey], the counter is scoped to [scope], which is
     * persisted under that key. If the persisted scope differs from the one supplied (including
     * when nothing has been persisted yet), the counter restarts and this call returns the value
     * supplied by [seed]. Omitting [scope] for an ordinal declared with a scope key is an invalid operation,
     * so -1 will be returned without the ordinal incrementing. For ordinals without a scope key, [scope] is
     * ignored even if supplied.
     */
    fun incrementAndGet(ordinal: Ordinal, scope: String? = null, seed: () -> Int = { 1 }): Int
}
