package io.embrace.android.embracesdk.injection

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.EmbraceSpansService
import io.embrace.android.embracesdk.internal.spans.EmbraceTracer
import io.embrace.android.embracesdk.internal.spans.SpansSinkImpl
import io.embrace.android.embracesdk.internal.spans.UninitializedSdkSpansService
import io.mockk.mockk
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class InitModuleImplTest {

    @Test
    fun testInitModuleImplDefaults() {
        val initModule = InitModuleImpl()
        assertTrue(initModule.clock is NormalizedIntervalClock)
        assertTrue(initModule.spansService is EmbraceSpansService)
    }

    @Test
    fun testInitModuleImplOverrideComponents() {
        val clock = FakeClock()
        val telemetryService = FakeTelemetryService()
        val spansSink = SpansSinkImpl()
        val spansService = UninitializedSdkSpansService()
        val openTelemetrySdk: OpenTelemetry = mockk()
        val tracer: Tracer = mockk()
        val currentSessionSpan: CurrentSessionSpan = mockk()
        val embraceTracer: EmbraceTracer = mockk()
        val initModule = InitModuleImpl(
            clock = clock,
            telemetryService = telemetryService,
            spansSink = spansSink,
            openTelemetrySdk = openTelemetrySdk,
            spansService = spansService,
            tracer = tracer,
            currentSessionSpan = currentSessionSpan,
            embraceTracer = embraceTracer,
        )
        assertSame(clock, initModule.clock)
        assertSame(telemetryService, initModule.telemetryService)
        assertSame(spansSink, initModule.spansSink)
        assertSame(openTelemetrySdk, initModule.openTelemetrySdk)
        assertSame(spansService, initModule.spansService)
        assertSame(tracer, initModule.tracer)
        assertSame(currentSessionSpan, initModule.currentSessionSpan)
        assertSame(embraceTracer, initModule.embraceTracer)
    }
}
