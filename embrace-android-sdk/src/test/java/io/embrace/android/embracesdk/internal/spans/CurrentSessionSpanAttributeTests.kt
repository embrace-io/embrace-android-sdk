package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.findSpanAttribute
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
        val span = currentSessionSpan.endSession(null).single()
        assertEquals("emb-session", span.name)

        // assert attributes added by default
        span.assertCommonSessionSpanAttrs()
        assertEquals("true", span.findSpanAttribute("emb.cold_start"))
    }

    @Test
    fun `attributes added to hot session span`() {
        // end the first session span then create another one
        currentSessionSpan.endSession(null)
        val span = currentSessionSpan.endSession(null).single()
        assertEquals("emb-session", span.name)

        // assert attributes added by default
        span.assertCommonSessionSpanAttrs()
        assertEquals("false", span.findSpanAttribute("emb.cold_start"))
    }

    private fun EmbraceSpanData.assertCommonSessionSpanAttrs() {
        assertNotNull(findSpanAttribute("emb.session_id"))
        assertEquals("ux.session", findSpanAttribute("emb.type"))
    }
}
