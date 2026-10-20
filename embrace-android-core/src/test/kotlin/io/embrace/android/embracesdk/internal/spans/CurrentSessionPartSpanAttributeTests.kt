package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.injection.TestInitServices
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
internal class CurrentSessionPartSpanAttributeTests(
    private val otelSdkMode: OtelSdkMode,
) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }

    private lateinit var spanRepository: SpanRepository
    private lateinit var currentSessionPartSpan: CurrentSessionPartSpan
    private val clock = FakeClock(1000L)

    @Before
    fun setup() {
        val initServices = TestInitServices(clock = clock, otelSdkMode = otelSdkMode)
        spanRepository = initServices.otelStack.spanRepository
        currentSessionPartSpan = initServices.otelStack.currentSessionPartSpan
        currentSessionPartSpan.initializeService(clock.now())
    }

    @Test
    fun `attributes added to cold start session part span`() {
        val span = currentSessionPartSpan.endSession(true).single()
        assertEquals("emb-session", span.name)

        // assert attributes added by default
        span.assertCommonSessionPartSpanAttrs()
    }

    @Test
    fun `attributes added to hot session part span`() {
        // end the first session part span then create another one
        currentSessionPartSpan.endSession(true)
        val span = currentSessionPartSpan.endSession(true).single()
        assertEquals("emb-session", span.name)

        // assert attributes added by default
        span.assertCommonSessionPartSpanAttrs()
    }

    private fun Span.assertCommonSessionPartSpanAttrs() {
        assertNotNull(attributes?.findAttributeValue(EmbSessionAttributes.EMB_SESSION_PART_ID))
        assertEquals("ux.session", attributes?.findAttributeValue("emb.type"))
    }
}
