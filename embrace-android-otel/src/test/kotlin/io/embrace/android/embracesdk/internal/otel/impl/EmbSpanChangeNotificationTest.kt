package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.TestConstants.TESTS_DEFAULT_USE_KOTLIN_SDK
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.createSdkOtelInstance
import io.embrace.android.embracesdk.internal.otel.inheritedApiMethodNames
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanFactory
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanFactoryImpl
import io.embrace.android.embracesdk.internal.otel.spans.OtelSpanStartArgs
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.spans.SpanServiceImpl
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.attributes.AnyValue
import io.opentelemetry.kotlin.getTracer
import io.opentelemetry.kotlin.tracing.Span
import io.opentelemetry.kotlin.tracing.SpanContext
import io.opentelemetry.kotlin.tracing.SpanCreationAction
import io.opentelemetry.kotlin.tracing.StatusData
import io.opentelemetry.kotlin.tracing.Tracer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Asserts that every mutating member of EmbSpan fires exactly one span-change
 * notification carrying the span that changed.
 */
internal class EmbSpanChangeNotificationTest {

    private lateinit var fakeClock: FakeClock
    private lateinit var otelClock: FakeOtelKotlinClock
    private lateinit var otelInstance: OpenTelemetry
    private lateinit var tracer: Tracer
    private lateinit var spanRepository: SpanRepository
    private lateinit var dataValidator: DataValidator
    private lateinit var spanFactory: EmbraceSpanFactory
    private lateinit var linkTarget: SpanContext
    private val observed = mutableListOf<EmbraceSdkSpan>()

    private val mutators: Map<String, Span.() -> Unit> = mapOf(
        "setStringAttribute" to { setStringAttribute("key-string", "value") },
        "setLongAttribute" to { setLongAttribute("key-long", 1L) },
        "setDoubleAttribute" to { setDoubleAttribute("key-double", 1.0) },
        "setBooleanAttribute" to { setBooleanAttribute("key-boolean", true) },
        "setStringListAttribute" to { setStringListAttribute("key-string-list", listOf("value")) },
        "setLongListAttribute" to { setLongListAttribute("key-long-list", listOf(1L)) },
        "setDoubleListAttribute" to { setDoubleListAttribute("key-double-list", listOf(1.0)) },
        "setBooleanListAttribute" to { setBooleanListAttribute("key-boolean-list", listOf(true)) },
        "setByteArrayAttribute" to { setByteArrayAttribute("key-bytes", byteArrayOf(1, 2)) },
        "setAnyValueAttribute" to { setAnyValueAttribute("key-any", AnyValue.StringValue("value")) },
        "addEvent" to { addEvent("an-event") },
        "addLink" to { addLink(linkTarget) },
        "setName" to { setName("renamed") },
        "setStatus" to { setStatus(StatusData.Error(null)) },
        "end" to { end() },
    )

    private val readOnly = setOf("getSpanContext", "getParent", "isRecording")

    @Before
    fun setup() {
        fakeClock = FakeClock()
        otelClock = FakeOtelKotlinClock(fakeClock)
        otelInstance = createSdkOtelInstance(clock = otelClock, useKotlinSdk = TESTS_DEFAULT_USE_KOTLIN_SDK)
        tracer = otelInstance.getTracer("test-tracer")
        val telemetryService = FakeTelemetryService()
        dataValidator = DataValidator(telemetryService = telemetryService)
        spanRepository = SpanRepository().apply { addSpanChangeListener(observed::add) }
        spanFactory = EmbraceSpanFactoryImpl(
            openTelemetryClock = otelClock,
            spanRepository = spanRepository,
            dataValidator = dataValidator,
            telemetryService = telemetryService,
        )
        fakeClock.tick(100)
        linkTarget = checkNotNull(createStartedSpan().spanContext)
        observed.clear()
    }

    @Test
    fun `every EmbSpan mutator notifies exactly once with the span that changed`() {
        mutators.forEach { (name, mutate) ->
            val impl = createStartedSpan()
            val embSpan = wrap(impl)
            observed.clear()
            embSpan.mutate()
            assertEquals("$name should notify exactly once", listOf(impl), observed)
        }
    }

    @Test
    fun `ending with an explicit timestamp notifies once`() {
        val impl = createStartedSpan()
        observed.clear()
        wrap(impl).end(timestamp = otelClock.now())
        assertEquals(listOf(impl), observed)
    }

    @Test
    fun `adding an event with an attribute block notifies once`() {
        val impl = createStartedSpan()
        observed.clear()
        wrap(impl).addEvent("an-event") { setStringAttribute("event-key", "value") }
        assertEquals(listOf(impl), observed)
    }

    @Test
    fun `adding a link with an attribute block notifies once`() {
        val impl = createStartedSpan()
        observed.clear()
        wrap(impl).addLink(linkTarget) { setStringAttribute("link-key", "value") }
        assertEquals(listOf(impl), observed)
    }

    @Test
    fun `every OTel span API method is classified as a mutator or read-only`() {
        val unclassified = declaredApi() - mutators.keys - readOnly
        assertEquals(
            "unclassified OTel span method(s): an otelKotlin bump added a member that must be classified",
            emptySet<String>(),
            unclassified,
        )
    }

    @Test
    fun `no stale classifications`() {
        val stale = (mutators.keys + readOnly) - declaredApi()
        assertEquals("classified method(s) no longer declared on the OTel span API", emptySet<String>(), stale)
    }

    @Test
    fun `EmbInvalidSpan never notifies`() {
        mutators.forEach { (name, mutate) ->
            observed.clear()
            EmbInvalidSpan(otelInstance).mutate()
            assertEquals("$name should not notify", emptyList<EmbraceSdkSpan>(), observed)
        }
    }

    @Test
    fun `spans created through the tracer notify on start and on each attribute set`() {
        val spanService = SpanServiceImpl(
            spanRepository = spanRepository,
            dataValidator = dataValidator,
            canStartNewSpan = { _, _, _ -> true },
            initCallback = {},
            embraceSpanFactory = spanFactory,
            tracerSupplier = { tracer },
            openTelemetrySupplier = { otelInstance },
        ).apply { initializeService(otelClock.now().nanosToMillis()) }

        observed.clear()
        EmbTracerProvider(otelInstance, spanService, otelClock)
            .getTracer("test-tracer")
            .startSpan("traced-span") {
                setStringAttribute("first", "1")
                setStringAttribute("second", "2")
            }

        // one for the start, then one per attribute written in the creation block
        assertEquals(3, observed.size)
        assertEquals(1, observed.distinct().size)
    }

    @Test
    fun `SpanCreationAction contributes no unclassified members`() {
        assertTrue(SpanCreationAction::class.java.inheritedApiMethodNames() - declaredApi() == emptySet<String>())
    }

    private fun declaredApi(): Set<String> = Span::class.java.inheritedApiMethodNames()

    private fun wrap(impl: EmbraceSdkSpan) = EmbSpan(impl = impl, clock = otelClock, openTelemetry = otelInstance)

    private fun createStartedSpan(): EmbraceSdkSpan = spanFactory.create(
        otelSpanStartArgs = OtelSpanStartArgs(
            name = "test-span",
            type = EmbType.Performance.Default,
            internal = false,
            private = false,
            tracer = tracer,
            openTelemetry = otelInstance,
        ),
    ).apply { start() }
}
