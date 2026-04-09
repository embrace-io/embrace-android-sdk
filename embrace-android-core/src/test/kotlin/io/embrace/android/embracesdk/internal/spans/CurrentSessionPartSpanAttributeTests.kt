package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.assertions.findAttributeValue
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.otel.spans.SpanSink
import io.opentelemetry.kotlin.semconv.SessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

internal class CurrentSessionPartSpanAttributeTests {

    private lateinit var spanRepository: SpanRepository
    private lateinit var spanSink: SpanSink
    private lateinit var currentSessionPartSpan: CurrentSessionPartSpan
    private lateinit var spanService: SpanService
    private val clock = FakeClock(1000L)

    @Before
    fun setup() {
        val initModule = FakeInitModule(clock = clock)
        spanRepository = initModule.openTelemetryModule.spanRepository
        spanSink = initModule.openTelemetryModule.spanSink
        currentSessionPartSpan = initModule.openTelemetryModule.currentSessionPartSpan
        spanService = initModule.openTelemetryModule.spanService
        spanService.initializeService(clock.now())
    }

    @Test
    fun `attributes added to cold start session span`() {
        val span = currentSessionPartSpan.endSession(true).single()
        assertEquals("emb-session", span.name)

        // assert attributes added by default
        span.assertCommonSessionSpanAttrs()
    }

    @Test
    fun `attributes added to hot session span`() {
        // end the first session span then create another one
        currentSessionPartSpan.endSession(true)
        val span = currentSessionPartSpan.endSession(true).single()
        assertEquals("emb-session", span.name)

        // assert attributes added by default
        span.assertCommonSessionSpanAttrs()
    }

    private fun EmbraceSpanData.assertCommonSessionSpanAttrs() {
        assertNotNull(attributes.findAttributeValue(SessionAttributes.SESSION_ID))
        assertEquals("ux.session", attributes.findAttributeValue("emb.type"))
    }
}
