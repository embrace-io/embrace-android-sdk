package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.arch.schema.KeySpan
import io.embrace.android.embracesdk.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.opentelemetry.api.trace.Tracer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EmbraceSpanBuilderTest {
    private val clock = FakeClock()
    private lateinit var tracer: Tracer

    @Before
    fun setup() {
        val initModule = FakeInitModule(clock)
        tracer = initModule.openTelemetryModule.tracer
    }

    @Test
    fun `check public span creation`() {
        val spanBuilder = EmbraceSpanBuilder(
            tracer = tracer,
            name = "test",
            telemetryType = EmbType.Performance.Default,
            internal = false,
            private = true,
            parent = null,
        )
        with(spanBuilder.getFixedAttributes().toSet()) {
            assertTrue(contains(PrivateSpan))
            assertTrue(contains(EmbType.Performance.Default))
            assertTrue(contains(KeySpan))
        }
        val span = spanBuilder.startSpan()
        assertTrue(span.isRecording)
        span.end()
        assertFalse(span.isRecording)
    }
}
