package io.embrace.android.embracesdk.injection

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.spans.EmbraceSpansService
import io.embrace.android.embracesdk.internal.spans.EmbraceTracer
import io.embrace.android.embracesdk.internal.spans.UninitializedSdkSpansService
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
        val spansService = UninitializedSdkSpansService()
        val embraceTracer = EmbraceTracer(spansService)
        val initModule = InitModuleImpl(
            clock = clock,
            spansService = spansService,
            tracer = embraceTracer
        )
        assertSame(clock, initModule.clock)
        assertSame(spansService, initModule.spansService)
        assertSame(embraceTracer, initModule.tracer)
    }
}
