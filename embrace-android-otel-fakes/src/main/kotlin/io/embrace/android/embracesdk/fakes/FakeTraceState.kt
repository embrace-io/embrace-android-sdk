package io.embrace.android.embracesdk.fakes

import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.tracing.model.TraceState

@OptIn(ExperimentalApi::class)
class FakeTraceState(
    private val state: Map<String, String> = emptyMap(),
) : TraceState {

    override fun asMap(): Map<String, String> = state

    override fun get(key: String): String? = state[key]

    override fun put(key: String, value: String): TraceState {
        return FakeTraceState(state + (key to value))
    }

    override fun remove(key: String): TraceState {
        return FakeTraceState(state - key)
    }
}
