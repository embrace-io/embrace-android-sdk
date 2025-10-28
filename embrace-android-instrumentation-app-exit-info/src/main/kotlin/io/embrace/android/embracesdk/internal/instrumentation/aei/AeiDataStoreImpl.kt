package io.embrace.android.embracesdk.internal.instrumentation.aei

import io.embrace.android.embracesdk.internal.arch.store.KeyValueStore

class AeiDataStoreImpl(private val store: KeyValueStore) : AeiDataStore {

    private companion object {
        private const val LAST_AEI_CRASH_NUMBER_KEY = "io.embrace.aeicrashnumber"
        private const val AEI_HASH_CODES = "io.embrace.aeiHashCode"
    }

    override var deliveredAeiIds: Set<String>
        get() = store.getStringSet(AEI_HASH_CODES) ?: emptySet()
        set(value) = store.edit { putStringSet(AEI_HASH_CODES, value) }

    override fun incrementAndGetAeiCrashNumber(): Int {
        return store.incrementAndGet(LAST_AEI_CRASH_NUMBER_KEY)
    }

    override fun incrementAndGetCrashNumber(): Int {
        return store.incrementAndGetCrashNumber()
    }
}
