package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeInternalTelemetryService
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.arch.schema.LinkType
import io.embrace.android.embracesdk.internal.otel.apiMethodNames
import io.embrace.android.embracesdk.internal.otel.createSdkOtelInstance
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.getTracer
import io.opentelemetry.kotlin.tracing.SpanContext
import io.opentelemetry.kotlin.tracing.StatusData
import io.opentelemetry.kotlin.tracing.Tracer
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Asserts that every mutating member of the span API fires exactly one span-change
 * notification carrying the span that changed.
 *
 * The no-op and late-mutation cases (rewriting an existing value, mutating before start or after
 * stop, exceeding a limit) are covered in `EmbraceSpanImplTest`.
 */
@RunWith(Parameterized::class)
internal class SpanChangeNotificationTest(
    private val otelSdkMode: OtelSdkMode,
) {

    private lateinit var fakeClock: FakeClock
    private lateinit var otelClock: FakeOtelKotlinClock
    private lateinit var otelInstance: OpenTelemetry
    private lateinit var tracer: Tracer
    private lateinit var spanRepository: SpanRepository
    private lateinit var telemetryService: FakeInternalTelemetryService
    private lateinit var spanFactory: EmbraceSpanFactory
    private lateinit var linkTarget: SpanContext
    private val observed = mutableListOf<EmbraceSdkSpan>()

    private val mutators: Map<String, EmbraceSdkSpan.() -> Unit> = mapOf(
        "start" to { start() },
        "stop" to { stop() },
        "stopWithErrorCode" to { stopWithErrorCode(ErrorCodeAttribute.Failure) },
        "addEvent" to { addEvent("an-event") },
        "recordException" to { recordException(RuntimeException("boom")) },
        "addSystemEvent" to { addSystemEvent("a-system-event", null, null) },
        "addAttribute" to { addAttribute("custom-key", "value") },
        "addCustomAttribute" to { addCustomAttribute("custom-key", 1L) },
        "addCustomEvent" to { addCustomEvent("an-event", null, mapOf("key" to true)) },
        "updateName" to { updateName("renamed") },
        "addLink" to { addLink(linkedSpanContext = linkTarget) },
        "addCustomLink" to { addCustomLink(linkTarget, mapOf("key" to 2.0)) },
        "addSystemLink" to { addSystemLink(linkTarget, LinkType.PreviousSessionPart) },
        "setSystemAttribute" to { setSystemAttribute("system-key-a", "value") },
        "addSystemAttribute" to { addSystemAttribute("system-key-b", "value") },
        "removeSystemAttribute" to { removeSystemAttribute(PRESET_KEY) },
        "setStatus" to { status = StatusData.Error(null) },
    )

    private val readOnly = setOf(
        "getSpanContext",
        "getTraceId",
        "getSpanId",
        "isRecording",
        "getParent",
        "getAutoTerminationMode",
        "getTerminationMode",
        "getStatus",
        "getSpanKind",
        "asNewContext",
        "asW3cTraceParent",
        "snapshot",
        "hasEmbraceAttribute",
        "getSystemAttribute",
        "getStartTimeMs",
        "attributes",
        "name",
        "events",
        "links",
    )

    @Before
    fun setup() {
        fakeClock = FakeClock()
        otelClock = FakeOtelKotlinClock(fakeClock)
        otelInstance = createSdkOtelInstance(clock = otelClock, useKotlinSdk = otelSdkMode.useKotlinSdk)
        tracer = otelInstance.getTracer("test-tracer")
        telemetryService = FakeInternalTelemetryService()
        spanRepository = SpanRepository().apply { addSpanChangeListener(observed::add) }
        spanFactory = EmbraceSpanFactoryImpl(
            openTelemetryClock = otelClock,
            spanRepository = spanRepository,
            dataValidator = DataValidator(telemetryService = telemetryService),
            telemetryService = telemetryService,
        )
        fakeClock.tick(100)
        linkTarget = checkNotNull(createSpan().apply { start() }.spanContext)
        observed.clear()
    }

    @Test
    fun `every mutator notifies exactly once with the span that changed`() {
        mutators.forEach { (name, mutate) ->
            val span = startedSpanFor(name)
            observed.clear()
            span.mutate()
            assertEquals("$name should notify exactly once", listOf(span), observed)
        }
    }

    @Test
    fun `linking to another span notifies once`() {
        val span = startedSpanFor("addLink")
        val other = createSpan().apply { start() }
        observed.clear()
        span.addLink(linkedSpan = other)
        assertEquals(listOf(span), observed)
    }

    @Test
    fun `every span API method is classified as a mutator or read-only`() {
        val unclassified = declaredApi() - mutators.keys - readOnly
        assertEquals(
            "unclassified span API method(s): classify in mutators (and cover it) or readOnly",
            emptySet<String>(),
            unclassified,
        )
    }

    @Test
    fun `no stale classifications`() {
        val stale = (mutators.keys + readOnly) - declaredApi()
        assertEquals("classified method(s) no longer declared on the span API", emptySet<String>(), stale)
    }

    private fun declaredApi(): Set<String> =
        EmbraceSdkSpan::class.java.apiMethodNames() + EmbraceSpan::class.java.apiMethodNames()

    private fun startedSpanFor(mutator: String): EmbraceSdkSpan = createSpan().apply {
        if (mutator != "start") {
            start()
            setSystemAttribute(PRESET_KEY, "preset-value")
        }
    }

    private fun createSpan(): EmbraceSdkSpan = spanFactory.create(
        otelSpanStartArgs = OtelSpanStartArgs(
            name = "test-span",
            type = EmbType.Performance.Default,
            internal = false,
            private = false,
            tracer = tracer,
            openTelemetry = otelInstance,
        ),
    )

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()

        const val PRESET_KEY = "preset-system-key"
    }
}
