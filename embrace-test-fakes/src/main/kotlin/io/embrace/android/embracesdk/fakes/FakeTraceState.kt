package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.tracing.model.TraceState

@OptIn(ExperimentalApi::class)
class FakeTraceState(
    private val state: Map<String, String> = emptyMap(),
) : TraceState {
    override fun asMap(): Map<String, String> = state

    override fun get(key: String): String? = state[key]
}
