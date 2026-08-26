package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.fakes.FakeSessionIdsProvider
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.TestConstants.TESTS_DEFAULT_USE_KOTLIN_SDK
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.createSdkOtelInstance
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.getTracer
import io.opentelemetry.kotlin.tracing.Tracer
import io.opentelemetry.kotlin.tracing.export.SpanProcessor
import io.opentelemetry.kotlin.tracing.export.compositeSpanProcessor
import io.opentelemetry.kotlin.tracing.model.ReadWriteSpan
import io.opentelemetry.kotlin.tracing.model.ReadableSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Asserts the current behavior that [SpanProcessor] does not invoke span change listeners.
 */
internal class SpanProcessorNotificationTest {

    private lateinit var fakeClock: FakeClock
    private lateinit var otelClock: FakeOtelKotlinClock
    private lateinit var otelInstance: OpenTelemetry
    private lateinit var tracer: Tracer
    private lateinit var spanExporter: FakeSpanExporter
    private lateinit var spanRepository: SpanRepository
    private lateinit var spanFactory: EmbraceSpanFactory
    private val observed = mutableListOf<EmbraceSdkSpan>()

    @Before
    fun setup() {
        fakeClock = FakeClock()
        otelClock = FakeOtelKotlinClock(fakeClock)
        spanExporter = FakeSpanExporter()
        otelInstance = createSdkOtelInstance(
            clock = otelClock,
            useKotlinSdk = TESTS_DEFAULT_USE_KOTLIN_SDK,
            tracerProvider = {
                export {
                    compositeSpanProcessor(
                        EmbraceSpanProcessor(
                            sessionIdsProvider = {
                                FakeSessionIdsProvider(
                                    userSessionId = "user-session-id",
                                    sessionPartId = "session-part-id",
                                )
                            },
                            processIdentifier = "test-process",
                            spanExporter = spanExporter,
                        ),
                        HostSpanProcessor(),
                    )
                }
            },
        )
        tracer = otelInstance.getTracer("test-tracer")
        val telemetryService = FakeTelemetryService()
        spanRepository = SpanRepository().apply { addSpanChangeListener(observed::add) }
        spanFactory = EmbraceSpanFactoryImpl(
            openTelemetryClock = otelClock,
            spanRepository = spanRepository,
            dataValidator = DataValidator(telemetryService = telemetryService),
            telemetryService = telemetryService,
        )
        fakeClock.tick(100)
    }

    @Test
    fun `attributes written by a span processor do not notify`() {
        val span = createSpan()
        observed.clear()

        span.start()
        assertEquals("start should be the only notification", listOf(span), observed)

        span.stop()
        assertEquals("stop should add exactly one more", listOf(span, span), observed)
    }

    @Test
    fun `attributes written by a span processor are absent from the snapshot`() {
        val span = createSpan().apply { start() }
        val snapshot = checkNotNull(span.snapshot()).attributes.orEmpty()

        listOf(
            HOST_PROCESSOR_KEY,
            EmbSessionAttributes.EMB_PROCESS_IDENTIFIER,
            EmbSessionAttributes.EMB_SESSION_PART_ID,
            EmbSessionAttributes.EMB_USER_SESSION_ID,
        ).forEach { key ->
            assertNull("$key should not reach the snapshot", snapshot.findAttributeValue(key))
        }
    }

    @Test
    fun `attributes written by a span processor do reach the exported span`() {
        createSpan().apply {
            start()
            stop()
        }

        val exported = spanExporter.exportedSpans.single { it.name == SPAN_NAME }.attributes
        assertEquals(HOST_PROCESSOR_VALUE, exported[HOST_PROCESSOR_KEY])
        assertEquals("test-process", exported[EmbSessionAttributes.EMB_PROCESS_IDENTIFIER])
        assertEquals("session-part-id", exported[EmbSessionAttributes.EMB_SESSION_PART_ID])
        assertEquals("user-session-id", exported[EmbSessionAttributes.EMB_USER_SESSION_ID])
    }

    private fun createSpan(): EmbraceSdkSpan = spanFactory.create(
        otelSpanStartArgs = OtelSpanStartArgs(
            name = SPAN_NAME,
            type = EmbType.Performance.Default,
            internal = false,
            private = false,
            tracer = tracer,
            openTelemetry = otelInstance,
        ),
    )

    private class HostSpanProcessor : SpanProcessor {
        override fun onStart(span: ReadWriteSpan, parentContext: Context) {
            span.setStringAttribute(HOST_PROCESSOR_KEY, HOST_PROCESSOR_VALUE)
        }

        override fun onEnding(span: ReadWriteSpan) {}
        override fun onEnd(span: ReadableSpan) {}
        override fun isStartRequired() = true
        override fun isEndRequired() = false
        override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success
        override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
    }

    private companion object {
        const val SPAN_NAME = "test-span"
        const val HOST_PROCESSOR_KEY = "host.processor.attribute"
        const val HOST_PROCESSOR_VALUE = "written-by-processor"
    }
}
