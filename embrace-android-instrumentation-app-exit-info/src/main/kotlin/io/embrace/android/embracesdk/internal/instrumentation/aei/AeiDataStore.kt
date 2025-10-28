package io.embrace.android.embracesdk.internal.instrumentation.aei

interface AeiDataStore {

    /**
     * Set of hashcodes derived from ApplicationExitInfo objects
     */
    var deliveredAeiIds: Set<String>

    /**
     * Increments and returns the AEI crash number ordinal. This is an integer that
     * increments on every NDK tombstone detected via an AEI trace. It allows us to check the % of AEI crashes that
     * didn't get delivered to the backend.
     */
    fun incrementAndGetAeiCrashNumber(): Int

    /**
     * Increments and returns the crash number ordinal.
     */
    fun incrementAndGetCrashNumber(): Int
}
