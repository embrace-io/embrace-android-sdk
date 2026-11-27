package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeInternalTelemetryService
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.fakes.FakeSpanProcessor
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.internal.otel.createSdkOtelInstance
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
import io.embrace.android.embracesdk.spans.SpanStartAction
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.attributes.AttributeContainer
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.getTracer
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.data.SpanLinkData
import io.opentelemetry.kotlin.tracing.model.ReadableSpan
import io.opentelemetry.kotlin.tracing.model.SpanLink
import io.opentelemetry.kotlin.tracing.sampling.Sampler
import io.opentelemetry.kotlin.tracing.sampling.SamplingResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
internal class SpanStartActionTest(
    private val otelSdkMode: OtelSdkMode,
) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }

    private val clock = FakeClock()
    private val sampledAttributes = mutableListOf<Map<String, Any>>()
    private val sampledLinks = mutableListOf<List<SpanLinkData>>()
    private val endedSpans = mutableListOf<ReadableSpan>()
    private val errorHandler = FakeErrorHandler(throwOnSdkError = false)
    private lateinit var openTelemetry: OpenTelemetry
    private lateinit var spanService: SpanServiceImpl

    @Before
    fun setup() {
        val otelClock = FakeOtelKotlinClock(clock)
        openTelemetry = createSdkOtelInstance(
            clock = otelClock,
            useKotlinSdk = otelSdkMode.useKotlinSdk,
            tracerProvider = {
                sampler { CapturingSampler(alwaysOn()) }
                export { FakeSpanProcessor(onEndAction = { endedSpans.add(it) }) }
            },
        )
        val spanRepository = SpanRepository(FakeErrorHandler())
        spanService = SpanServiceImpl(
            spanRepository = spanRepository,
            dataValidator = DataValidator(telemetryService = FakeInternalTelemetryService()),
            canStartNewSpan = { _, _, _ -> true },
            embraceSpanFactory = EmbraceSpanFactoryImpl(
                openTelemetryClock = otelClock,
                spanRepository = spanRepository,
                dataValidator = DataValidator(telemetryService = FakeInternalTelemetryService()),
                redactionFunction = { key, value -> if (key == "password") "****" else value },
                telemetryService = FakeInternalTelemetryService(),
                errorHandler = errorHandler,
            ),
            tracer = openTelemetry.getTracer("test"),
            openTelemetry = openTelemetry,
        )
    }

    @Test
    fun `sampler sees attributes and links added by the start action`() {
        val linked = spanService.startSpan(name = "linked", internal = false)
        val span = spanService.startSpan(name = "test", internal = false, startAction = {
            addAttribute("key", "value")
            addAttribute("password", "hunter2")
            addEvent("event")
            addLink(linked, mapOf("link-key" to "link-value"))
        })
        assertTrue(span.isRecording)

        val attrs = sampledAttributes.last()
        assertEquals("value", attrs["key"])
        assertEquals("****", attrs["password"])
        val link = sampledLinks.last().single()
        assertEquals(linked.spanId, link.spanContext.spanId)
        assertEquals("link-value", link.attributes["link-key"])

        assertTrue(span.stop())
        val exported = endedSpans.single { it.name == "test" }
        assertEquals("value", exported.attributes["key"])
        assertEquals(1, exported.links.size)
        assertEquals("event", exported.events.single().name)
    }

    @Test
    fun `start without action passes nothing extra to sampler`() {
        spanService.startSpan(name = "test", internal = false)
        assertEquals(emptyList<SpanLinkData>(), sampledLinks.single())
        assertTrue(sampledAttributes.single().keys.none { it == "key" })
    }

    @Test
    fun `throwing start action starts span without its data`() {
        val linked = spanService.startSpan(name = "linked", internal = false)
        val span = spanService.startSpan(name = "test", internal = false, startAction = {
            addAttribute("key", "value")
            addEvent("event")
            addLink(linked)
            error("boom")
        })
        assertTrue(span.isRecording)
        assertTrue(sampledAttributes.last().keys.none { it == "key" })
        assertTrue(sampledLinks.last().isEmpty())

        assertTrue(span.stop())
        val exported = endedSpans.single { it.name == "test" }
        assertTrue(exported.attributes.keys.none { it == "key" })
        assertTrue(exported.links.isEmpty())
        assertTrue(exported.events.isEmpty())
        assertEquals("boom", errorHandler.warningMessages.single().throwable?.message)
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `start action respects attribute limits`() {
        val max = DataValidator(telemetryService = FakeInternalTelemetryService()).otelLimitsConfig.getMaxCustomAttributeCount()
        val action: SpanStartAction.() -> Unit = {
            repeat(max + 5) { addAttribute("key$it", "value") }
        }
        spanService.startSpan(name = "test", internal = false, startAction = action)
        assertEquals(max, sampledAttributes.single().keys.count { it.startsWith("key") })
    }

    private inner class CapturingSampler(private val delegate: Sampler) : Sampler {
        override val description: String = "capturing"

        override fun shouldSample(
            context: Context,
            traceIdBytes: ByteArray,
            name: String,
            spanKind: SpanKind,
            attributes: AttributeContainer,
            links: List<SpanLink>,
        ): SamplingResult {
            sampledAttributes.add(attributes.attributes.toMap())
            sampledLinks.add(links.toList())
            return delegate.shouldSample(context, traceIdBytes, name, spanKind, attributes, links)
        }
    }
}
