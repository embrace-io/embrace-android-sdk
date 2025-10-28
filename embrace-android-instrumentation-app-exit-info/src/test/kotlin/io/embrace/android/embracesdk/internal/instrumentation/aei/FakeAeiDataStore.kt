package io.embrace.android.embracesdk.internal.instrumentation.aei

internal class FakeAeiDataStore : AeiDataStore {

    private var aeiCount = 0
    private var crashCount = 0

    override var deliveredAeiIds: Set<String> = emptySet()

    override fun incrementAndGetAeiCrashNumber(): Int {
        return aeiCount++
    }

    override fun incrementAndGetCrashNumber(): Int {
        return crashCount++
    }
}
