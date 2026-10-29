package io.embrace.android.embracesdk.testcases.features

import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.instrumentation.bytecode.OkHttpBytecodeEntrypoint
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.semconv.EmbNetworkRequestAttributes
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbracePayloadAssertionInterface
import io.opentelemetry.kotlin.semconv.HttpAttributes
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(ParameterizedRobolectricTestRunner::class)
internal class OkHttpTraceparentFeatureTest(
    private val otelSdkMode: OtelSdkMode,
) {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode = otelSdkMode)

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `network span is a child of the sdk span named in the inbound traceparent`() {
        var expectedTraceId: String? = null
        var expectedParentSpanId: String? = null
        testRule.runTest(
            persistedRemoteConfig = INJECTION_AND_FORWARDING_ENABLED,
            testCaseAction = {
                recordSession {
                    val parent = checkNotNull(embrace.createSpan(name = "load search results"))
                    parent.start()

                    expectedTraceId = parent.traceId
                    expectedParentSpanId = parent.spanId

                    executeRequest("00-${parent.traceId}-${parent.spanId}-01")
                    parent.stop()
                }
            },
            assertAction = {
                val sent = server.takeRequest()
                with(getNetworkSpan()) {
                    assertEquals(expectedTraceId, traceId)
                    assertEquals(expectedParentSpanId, parentSpanId)
                    assertEquals("00-$expectedTraceId-$spanId-01", sent.traceparent())
                    assertEquals(sent.traceparent(), attributes?.findAttributeValue(EmbNetworkRequestAttributes.EMB_W3C_TRACEPARENT))
                }
                assertEquals(TRACESTATE, sent.getHeader(TRACESTATE_HEADER))
            }
        )
    }

    @Test
    fun `network span is a child of a span this sdk did not create and keeps its flags`() {
        testRule.runTest(
            persistedRemoteConfig = INJECTION_AND_FORWARDING_ENABLED,
            testCaseAction = {
                recordSession {
                    executeRequest(REMOTE_TRACEPARENT_UNSAMPLED)
                }
            },
            assertAction = {
                val sent = server.takeRequest()
                with(getNetworkSpan()) {
                    assertEquals(REMOTE_TRACE_ID, traceId)
                    assertEquals(REMOTE_SPAN_ID, parentSpanId)
                    assertEquals("00-$REMOTE_TRACE_ID-$spanId-00", sent.traceparent())
                    assertEquals(sent.traceparent(), attributes?.findAttributeValue(EmbNetworkRequestAttributes.EMB_W3C_TRACEPARENT))
                }
                assertEquals(TRACESTATE, sent.getHeader(TRACESTATE_HEADER))
            }
        )
    }

    @Test
    fun `inbound traceparent is sent unchanged when injection is disabled`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(traceparentInjectionPctEnabled = 0f),
            testCaseAction = {
                recordSession {
                    executeRequest(REMOTE_TRACEPARENT_UNSAMPLED)
                }
            },
            assertAction = {
                val sent = server.takeRequest()
                with(getNetworkSpan()) {
                    assertEquals(REMOTE_TRACE_ID, traceId)
                    assertEquals(REMOTE_SPAN_ID, parentSpanId)
                }
                assertEquals(REMOTE_TRACEPARENT_UNSAMPLED, sent.traceparent())
                assertEquals(TRACESTATE, sent.getHeader(TRACESTATE_HEADER))
            }
        )
    }

    private fun executeRequest(traceparent: String) {
        server.enqueue(MockResponse().setResponseCode(200))
        // applies the same interceptors as the bytecode instrumentation of OkHttpClient.Builder.build()
        val client = OkHttpClient.Builder().also(OkHttpBytecodeEntrypoint::build).build()
        val request = Request.Builder()
            .url(server.url("/search"))
            .header(TRACEPARENT_HEADER, traceparent)
            .header(TRACESTATE_HEADER, TRACESTATE)
            .build()
        client.newCall(request).execute().close()
    }

    private fun RecordedRequest.traceparent(): String? = getHeader(TRACEPARENT_HEADER)

    private fun EmbracePayloadAssertionInterface.getNetworkSpan(): Span =
        checkNotNull(getSingleSessionEnvelope().data.spans).single {
            it.attributes?.findAttributeValue(HttpAttributes.HTTP_REQUEST_METHOD) != null
        }

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()

        private const val TRACEPARENT_HEADER = "traceparent"
        private const val TRACESTATE_HEADER = "tracestate"
        private const val TRACESTATE = "vendor=value,other=1"
        private const val REMOTE_TRACE_ID = "0af7651916cd43dd8448eb211c80319c"
        private const val REMOTE_SPAN_ID = "b7ad6b7169203331"
        private const val REMOTE_TRACEPARENT_UNSAMPLED = "00-$REMOTE_TRACE_ID-$REMOTE_SPAN_ID-00"

        private val INJECTION_AND_FORWARDING_ENABLED = RemoteConfig(
            traceparentInjectionPctEnabled = 100f,
            nsfPctEnabled = 100f,
        )
    }
}
