package io.embrace.android.embracesdk.testcases.features

import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkRequestDataSource
import io.embrace.android.embracesdk.internal.instrumentation.network.RequestEndData
import io.embrace.android.embracesdk.internal.instrumentation.network.RequestStartData
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.opentelemetry.kotlin.semconv.UrlAttributes
import io.opentelemetry.kotlin.tracing.createSpanContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(ParameterizedRobolectricTestRunner::class)
internal class NetworkSpanParentFeatureTest(
    private val otelSdkMode: OtelSdkMode,
) {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode = otelSdkMode)

    @Test
    fun `network spans started while a span is the network span parent are its children until it ends`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    val checkout = checkNotNull(embrace.startSpan("checkout"))
                    embrace.setNetworkSpanParent(checkout)
                    runRequest(CHILD_URL)
                    val inFlightId = startRequest(IN_FLIGHT_URL)
                    checkout.stop()
                    endRequest(inFlightId, IN_FLIGHT_URL)
                    runRequest(AFTER_END_URL)
                }
            },
            assertAction = {
                val spans = checkNotNull(getSingleSessionEnvelope().data.spans)
                val checkout = spans.single { it.name == "checkout" }
                assertParent(checkout, spans.networkSpan(CHILD_URL))
                assertParent(checkout, spans.networkSpan(IN_FLIGHT_URL))
                assertNotEquals(checkout.traceId, spans.networkSpan(AFTER_END_URL).traceId)
            }
        )
    }

    @Test
    fun `an unsampled span context the SDK doesn't track is used as the network span parent`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.setNetworkSpanParent(createSpanContext(REMOTE_TRACE_ID, REMOTE_SPAN_ID) { isSampled = false })
                    runRequest(CHILD_URL)
                }
            },
            assertAction = {
                val span = checkNotNull(getSingleSessionEnvelope().data.spans).networkSpan(CHILD_URL)
                assertEquals(REMOTE_TRACE_ID, span.traceId)
                assertEquals(REMOTE_SPAN_ID, span.parentSpanId)
            }
        )
    }

    @Test
    fun `the network span parent is cleared when the user session ends but not when the app is backgrounded`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.setNetworkSpanParent(createSpanContext(REMOTE_TRACE_ID, REMOTE_SPAN_ID) { isSampled = true })
                }
                recordSession {
                    runRequest(CHILD_URL)
                    clock.tick(10000)
                    embrace.endUserSession()
                    runRequest(AFTER_END_URL)
                }
            },
            assertAction = {
                val spans = getSessionEnvelopes(3).flatMap { it.data.spans.orEmpty() }
                assertEquals(REMOTE_SPAN_ID, spans.networkSpan(CHILD_URL).parentSpanId)
                assertNotEquals(REMOTE_TRACE_ID, spans.networkSpan(AFTER_END_URL).traceId)
            }
        )
    }

    private fun EmbraceActionInterface.startRequest(url: String): String =
        checkNotNull(findDataSource<NetworkRequestDataSource>().startRequest(RequestStartData(url, "GET", clock.now())))

    private fun EmbraceActionInterface.endRequest(id: String, url: String) {
        findDataSource<NetworkRequestDataSource>().endRequest(RequestEndData(id, url, clock.now(), clock.now(), 200))
    }

    private fun EmbraceActionInterface.runRequest(url: String) = endRequest(startRequest(url), url)

    private fun List<Span>.networkSpan(url: String): Span = single { it.attributes?.findAttributeValue(UrlAttributes.URL_FULL) == url }

    private fun assertParent(parent: Span, child: Span) {
        assertEquals(parent.traceId, child.traceId)
        assertEquals(parent.spanId, child.parentSpanId)
    }

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()

        private const val CHILD_URL = "https://example.com/cart"
        private const val IN_FLIGHT_URL = "https://example.com/payment"
        private const val AFTER_END_URL = "https://example.com/receipt"
        private const val REMOTE_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"
        private const val REMOTE_SPAN_ID = "00f067aa0ba902b7"
    }
}
