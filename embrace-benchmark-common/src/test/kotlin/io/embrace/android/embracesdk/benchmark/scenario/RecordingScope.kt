package io.embrace.android.embracesdk.benchmark.scenario

import io.embrace.android.embracesdk.internal.api.SdkApi
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.mockk.every
import io.mockk.mockk

/**
 * Runs a scenario against a stand-in SDK on a clock of its own, recording what the scenario asked of it.
 */
internal class RecordingScope(delegate: SdkApi = standInSdk()) : ScenarioScope {

    private val sdk = CountingSdkApi(delegate)

    override val embrace: SdkApi = sdk

    var elapsedMs: Long = 0
        private set

    var backgroundTransitions: Int = 0
        private set

    val apiCalls: Int
        get() = sdk.calls

    override val nowMs: Long
        get() = START_TIME_MS + elapsedMs

    override fun advanceTime(ms: Long) {
        elapsedMs += ms
    }

    override fun backgroundAndReturn(ms: Long) {
        backgroundTransitions++
        elapsedMs += ms
    }

    companion object {
        const val START_TIME_MS = 1_692_201_601_000L
    }
}

/**
 * A stand-in SDK that accepts every call, and runs the code of each span it is asked to record around it.
 */
internal fun standInSdk(): SdkApi = mockk(relaxed = true) {
    every { recordSpan<Any?>(any(), any(), any(), any(), any(), any()) } answers { lastArg<() -> Any?>().invoke() }
}

private class CountingSdkApi(private val delegate: SdkApi) : SdkApi by delegate {

    var calls: Int = 0
        private set

    override fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan?,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        autoTerminationMode: AutoTerminationMode,
        code: () -> T,
    ): T {
        calls++
        return delegate.recordSpan(name, parent, attributes, events, autoTerminationMode, code)
    }

    override fun recordNetworkRequest(networkRequest: EmbraceNetworkRequest) {
        calls++
        delegate.recordNetworkRequest(networkRequest)
    }

    override fun addBreadcrumb(message: String) {
        calls++
        delegate.addBreadcrumb(message)
    }

    override fun logInfo(message: String) {
        calls++
        delegate.logInfo(message)
    }

    override fun logWarning(message: String) {
        calls++
        delegate.logWarning(message)
    }

    override fun logError(message: String) {
        calls++
        delegate.logError(message)
    }
}
