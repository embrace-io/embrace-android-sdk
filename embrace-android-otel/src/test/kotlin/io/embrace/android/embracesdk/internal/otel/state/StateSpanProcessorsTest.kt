package io.embrace.android.embracesdk.internal.otel.state

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.fakes.FakeSpanProcessor
import io.embrace.android.embracesdk.internal.otel.createSdkOtelInstance
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanProcessor
import io.opentelemetry.kotlin.tracing.export.compositeSpanProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

internal class StateSpanProcessorsTest {

    @Test
    fun `an app's processor sees the values on a span as it starts and ends, the span never has them, and they are let go`() {
        var state: StateStamp = FakeStateStamp(3, mapOf("network" to "wifi"))
        val tracker = SpanStateTracker({ state }, { null }, keepUntilReleased = true)
        val stored = FakeSpanExporter()
        val started = mutableListOf<Map<String, Any>>()
        val ended = mutableListOf<Map<String, Any>>()
        val appProcessor = FakeSpanProcessor(
            onStartAction = { span ->
                started.add(span.attributes.toMap())
                // a write goes through to the span itself
                span.setStringAttribute("set-by-the-app", "at-start")
            },
            onEndAction = { ended.add(it.attributes.toMap()) },
        )
        val tracer = createSdkOtelInstance(
            useKotlinSdk = true,
            tracerProvider = {
                export {
                    compositeSpanProcessor(
                        EmbraceSpanProcessor({ null }, processIdentifier = "pid", spanExporter = stored, stateTracker = tracker),
                        StateDecoratingSpanProcessor(appProcessor, tracker),
                        SpanStateReleaseProcessor(tracker),
                    )
                }
            },
            clock = FakeOtelKotlinClock(FakeClock()),
        ).tracerProvider.getTracer("test")

        val span = tracer.startSpan("span")
        assertEquals(1, tracker.trackedSpans())
        state = FakeStateStamp(4, mapOf("network" to "none"))
        span.end()

        assertEquals("wifi", started.single()["emb.app.state.value.network"])
        with(ended.single()) {
            assertEquals("wifi", get("emb.app.state.value.network"))
            assertEquals("none", get("emb.app.state.end_value.network"))
            assertEquals(4L, get(StateAttributes.END_SEQUENCE))
            assertEquals("at-start", get("set-by-the-app"))
        }
        with(stored.exportedSpans.single().attributes) {
            assertEquals("at-start", get("set-by-the-app"))
            assertEquals(3L, get(StateAttributes.SEQUENCE))
            assertFalse(keys.any { it.startsWith(StateAttributes.VALUE_PREFIX) || it.startsWith(StateAttributes.END_VALUE_PREFIX) })
        }
        assertEquals(0, tracker.trackedSpans())
    }
}
