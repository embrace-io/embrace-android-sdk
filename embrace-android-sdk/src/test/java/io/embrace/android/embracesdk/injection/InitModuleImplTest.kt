package io.embrace.android.embracesdk.injection

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryClock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpanImpl
import io.embrace.android.embracesdk.internal.spans.EmbraceSpansService
import io.embrace.android.embracesdk.internal.spans.SpansSinkImpl
import io.embrace.android.embracesdk.telemetry.EmbraceTelemetryService
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
        assertTrue(initModule.telemetryService is EmbraceTelemetryService)
        assertTrue(initModule.spansSink is SpansSinkImpl)
        assertTrue(initModule.spansService is EmbraceSpansService)
        assertTrue(initModule.currentSessionSpan is CurrentSessionSpanImpl)
    }

    @Test
    fun testInitModuleImplOverrideComponents() {
        val clock = FakeClock()
        val openTelemetryClock = FakeOpenTelemetryClock(clock)
        val initModule = InitModuleImpl(
            clock = clock,
            openTelemetryClock = openTelemetryClock
        )
        assertSame(clock, initModule.clock)
    }
}
