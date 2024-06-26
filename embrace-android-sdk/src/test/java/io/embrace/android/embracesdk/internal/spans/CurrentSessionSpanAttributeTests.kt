package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.findAttributeValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

internal class CurrentSessionSpanAttributeTests {

    private lateinit var spanRepository: SpanRepository
    private lateinit var spanSink: SpanSink
    private lateinit var currentSessionSpan: CurrentSessionSpan
    private lateinit var spanService: SpanService
    private val clock = FakeClock(1000L)

    @Before
    fun setup() {
        val initModule = FakeInitModule(clock = clock)
        spanRepository = initModule.openTelemetryModule.spanRepository
        spanSink = initModule.openTelemetryModule.spanSink
        currentSessionSpan = initModule.openTelemetryModule.currentSessionSpan
        spanService = initModule.openTelemetryModule.spanService
        spanService.initializeService(clock.now())
    }

    @Test
    fun `attributes added to cold start session span`() {
        val span = currentSessionSpan.endSession(true).single()
        assertEquals("emb-session", span.name)

        // assert attributes added by default
        span.assertCommonSessionSpanAttrs()
    }

    @Test
    fun `attributes added to hot session span`() {
        // end the first session span then create another one
        currentSessionSpan.endSession(true)
        val span = currentSessionSpan.endSession(true).single()
        assertEquals("emb-session", span.name)

        // assert attributes added by default
        span.assertCommonSessionSpanAttrs()
    }

    private fun EmbraceSpanData.assertCommonSessionSpanAttrs() {
        assertNotNull(attributes.findAttributeValue("emb.session_id"))
        assertEquals("ux.session", attributes.findAttributeValue("emb.type"))
    }
}
