package io.embrace.android.embracesdk.internal.store.file

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.store.Ordinal
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import io.embrace.android.embracesdk.internal.store.PersistedSdkStateStore

/**
 * Scopes [Ordinal.APP_VERSION_STARTUP] in [ordinals], keeping its scope in [state]. [state] must persist the scope
 * (the prefs store doesn't), or the counter resets on every launch.
 *
 * The counter is reset before the scope is persisted, so if the process dies in between, the next launch resets again
 * and the first two startups on a new scope both report the seed value.
 */
class ScopedOrdinalStore(
    private val ordinals: MmapOrdinalStore,
    private val state: PersistedSdkStateStore,
    private val errorHandler: ErrorHandler,
) : OrdinalStore {

    private val lock = Any()

    override fun incrementAndGet(ordinal: Ordinal, scope: String?, seed: () -> Int): Int {
        if (ordinal != Ordinal.APP_VERSION_STARTUP) {
            return ordinals.incrementAndGet(ordinal, seed = seed)
        }
        if (scope == null) {
            return -1
        }
        return synchronized(lock) {
            errorHandler.guardSdkCode(SdkErrorType.PayloadStorageFail, -1) {
                if (state.snapshot().appVersionStartupScope == scope) {
                    ordinals.incrementAndGet(ordinal, seed = seed)
                } else {
                    seed().also { value ->
                        ordinals.reset(ordinal, value)
                        state.update { appVersionStartupScope = scope }
                    }
                }
            }
        }
    }
}
