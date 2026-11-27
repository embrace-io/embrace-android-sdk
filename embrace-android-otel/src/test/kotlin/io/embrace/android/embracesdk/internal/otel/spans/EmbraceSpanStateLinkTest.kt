package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeInternalTelemetryService
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.fakeOpenTelemetry
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.createSdkOtelInstance
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
import io.embrace.android.embracesdk.internal.otel.state.StateExportMode
import io.embrace.android.embracesdk.internal.otel.state.StateTelemetry
import io.embrace.android.embracesdk.internal.state.AppStateKeys
import io.embrace.android.embracesdk.internal.state.StateDefinition
import io.embrace.android.embracesdk.internal.state.StateRecorder
import io.opentelemetry.kotlin.getTracer
import io.opentelemetry.kotlin.tracing.Tracer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
internal class EmbraceSpanStateLinkTest(
    private val otelSdkMode: OtelSdkMode,
) {
    private lateinit var clock: FakeClock
    private lateinit var spanExporter: FakeSpanExporter
    private lateinit var tracer: Tracer
    private lateinit var spanRepository: SpanRepository
    private lateinit var telemetryService: FakeInternalTelemetryService
    private lateinit var recorder: StateRecorder

    @Before
    fun setUp() {
        clock = FakeClock(START_MS)
        spanExporter = FakeSpanExporter()
        telemetryService = FakeInternalTelemetryService()
        spanRepository = SpanRepository(FakeErrorHandler())
        val otelClock = FakeOtelKotlinClock(clock)
        tracer = createSdkOtelInstance(
            clock = otelClock,
            useKotlinSdk = otelSdkMode.useKotlinSdk,
            tracerProvider = {
                export {
                    EmbraceSpanProcessor(
                        sessionIdsProvider = { null },
                        processIdentifier = "test-process",
                        spanExporter = spanExporter,
                    )
                }
            },
        ).getTracer("test-tracer")
        recorder = StateRecorder(clock)
    }

    @Test
    fun `a span no state changed under carries only its start number`() {
        declareStates()
        clock.tick(10)
        val span = createSpan(StateExportMode.ASSOCIATION)

        assertTrue(span.start())
        assertEquals(mapOf(AppStateKeys.SEQ to "2"), span.snapshotStateAttributes())
        clock.tick(10)
        assertTrue(span.stop())

        assertEquals(mapOf<String, Any>(AppStateKeys.SEQ to 2L), exportedStateAttributes())
        assertEquals(mapOf(AppStateKeys.SEQ to "2"), span.snapshotStateAttributes())
    }

    @Test
    fun `a span a state changed under carries both numbers, as text in the snapshot and long on the exported span`() {
        declareStates()
        clock.tick(10)
        val span = createSpan(StateExportMode.ASSOCIATION)
        assertTrue(span.start())
        clock.tick(10)
        recorder.update(NETWORK, "cell", false, clock.now())
        assertEquals(mapOf(AppStateKeys.SEQ to "2"), span.snapshotStateAttributes())
        clock.tick(10)
        assertTrue(span.stop())

        assertEquals(
            mapOf<String, Any>(AppStateKeys.SEQ to 2L, AppStateKeys.END_SEQ to 3L),
            exportedStateAttributes(),
        )
        assertEquals(mapOf(AppStateKeys.SEQ to "2", AppStateKeys.END_SEQ to "3"), span.snapshotStateAttributes())
    }

    @Test
    fun `the verbose rendering adds the values at the start and only the values that differed at the end`() {
        declareStates()
        clock.tick(10)
        val span = createSpan(StateExportMode.VERBOSE)
        assertTrue(span.start())
        clock.tick(10)
        recorder.update(NETWORK, "cell", false, clock.now())
        clock.tick(10)
        assertTrue(span.stop())

        assertEquals(
            mapOf<String, Any>(
                AppStateKeys.SEQ to 2L,
                AppStateKeys.END_SEQ to 3L,
                "${AppStateKeys.VALUE_PREFIX}$NETWORK" to "wifi",
                "${AppStateKeys.VALUE_PREFIX}$SCREEN" to "Initializing",
                "${AppStateKeys.VALUE_TYPE_PREFIX}$SCREEN" to AppStateKeys.VALUE_TYPE_SYSTEM,
                "${AppStateKeys.END_VALUE_PREFIX}$NETWORK" to "cell",
            ),
            exportedStateAttributes(),
        )
        // Embrace's own snapshot never carries the values
        assertEquals(mapOf(AppStateKeys.SEQ to "2", AppStateKeys.END_SEQ to "3"), span.snapshotStateAttributes())
    }

    @Test
    fun `a span that started before any state exists has an end number and no start number`() {
        val span = createSpan(StateExportMode.ASSOCIATION)
        assertTrue(span.start())
        clock.tick(10)
        assertEquals(emptyMap<String, String>(), span.snapshotStateAttributes())
        recorder.declare(StateDefinition(NETWORK), "wifi", false, clock.now())
        clock.tick(10)
        assertTrue(span.stop())

        assertEquals(mapOf<String, Any>(AppStateKeys.END_SEQ to 1L), exportedStateAttributes())
        assertEquals(mapOf(AppStateKeys.END_SEQ to "1"), span.snapshotStateAttributes())
    }

    @Test
    fun `a span that started before any state exists carries the end values in the verbose rendering`() {
        val span = createSpan(StateExportMode.VERBOSE)
        assertTrue(span.start())
        clock.tick(10)
        recorder.declare(StateDefinition(NETWORK), "wifi", false, clock.now())
        assertTrue(span.stop())

        assertEquals(
            mapOf<String, Any>(
                AppStateKeys.END_SEQ to 1L,
                "${AppStateKeys.END_VALUE_PREFIX}$NETWORK" to "wifi",
            ),
            exportedStateAttributes(),
        )
    }

    @Test
    fun `a span given its times is placed by them, and a time equal to a change counts as after it`() {
        declareStates()
        clock.tick(100)
        recorder.update(NETWORK, "cell", false, clock.now()) // seq 3 at START_MS + 100
        clock.tick(100)
        recorder.update(NETWORK, "none", false, clock.now()) // seq 4 at START_MS + 200
        clock.tick(100)
        val span = createSpan(StateExportMode.ASSOCIATION)

        assertTrue(span.start(START_MS + 100))
        assertTrue(span.stop(null, START_MS + 200))

        assertEquals(
            mapOf<String, Any>(AppStateKeys.SEQ to 3L, AppStateKeys.END_SEQ to 4L),
            exportedStateAttributes(),
        )
    }

    @Test
    fun `a span given times between two changes is placed after the earlier one`() {
        declareStates()
        clock.tick(100)
        recorder.update(NETWORK, "cell", false, clock.now()) // seq 3 at START_MS + 100
        clock.tick(100)
        val span = createSpan(StateExportMode.ASSOCIATION)

        assertTrue(span.start(START_MS + 50))
        assertTrue(span.stop(null, START_MS + 99))

        assertEquals(mapOf<String, Any>(AppStateKeys.SEQ to 2L), exportedStateAttributes())
    }

    @Test
    fun `a span given a start before the first change has no start number`() {
        declareStates()
        clock.tick(100)
        val span = createSpan(StateExportMode.ASSOCIATION)

        assertTrue(span.start(START_MS - 50))
        assertTrue(span.stop(null, START_MS))

        assertEquals(mapOf<String, Any>(AppStateKeys.END_SEQ to 2L), exportedStateAttributes())
    }

    @Test
    fun `a span given a start older than the window of snapshots has no link at all`() {
        recorder = StateRecorder(clock, windowSize = 2)
        declareStates() // seq 1 and 2 at START_MS
        clock.tick(100)
        recorder.update(NETWORK, "cell", false, clock.now()) // seq 3 at START_MS + 100
        clock.tick(100)
        val span = createSpan(StateExportMode.VERBOSE)

        assertTrue(span.start(START_MS - 50))
        assertTrue(span.stop())

        assertEquals(emptyMap<String, Any>(), exportedStateAttributes())
        assertEquals(emptyMap<String, String>(), span.snapshotStateAttributes())
    }

    @Test
    fun `a span with no state telemetry carries nothing`() {
        declareStates()
        val span = createSpan(stateTelemetry = null)

        assertTrue(span.start())
        recorder.update(NETWORK, "cell", false, clock.now())
        assertEquals(emptyMap<String, String>(), span.snapshotStateAttributes())
        assertTrue(span.stop())

        assertEquals(emptyMap<String, Any>(), exportedStateAttributes())
        assertEquals(emptyMap<String, String>(), span.snapshotStateAttributes())
    }

    private fun declareStates() {
        recorder.declare(StateDefinition(NETWORK), "wifi", false, clock.now())
        recorder.declare(StateDefinition(SCREEN), "Initializing", true, clock.now())
    }

    private fun createSpan(mode: StateExportMode): EmbraceSdkSpan = createSpan(
        StateTelemetry(recorder, exportMode = {
            mode
        }),
    )

    private fun createSpan(stateTelemetry: StateTelemetry?): EmbraceSdkSpan {
        val factory = EmbraceSpanFactoryImpl(
            openTelemetryClock = FakeOtelKotlinClock(clock),
            spanRepository = spanRepository,
            dataValidator = DataValidator(telemetryService = telemetryService),
            stopCallback = {},
            redactionFunction = null,
            telemetryService = telemetryService,
            errorHandler = FakeErrorHandler(),
            stateTelemetry = stateTelemetry,
        )
        return factory.create(
            OtelSpanStartArgs(
                name = "test-span",
                type = EmbType.Performance.Default,
                internal = false,
                private = false,
                tracer = tracer,
                openTelemetry = fakeOpenTelemetry(otelSdkMode),
            ),
        )
    }

    private fun exportedStateAttributes(): Map<String, Any> {
        val exported = spanExporter.exportedSpans.single()
        assertNotNull(exported)
        return exported.attributes.filterKeys { it.startsWith(AppStateKeys.STEM) }
    }

    private fun EmbraceSdkSpan.snapshotStateAttributes(): Map<String, String?> =
        checkNotNull(snapshot()).attributes.orEmpty()
            .filter { it.key?.startsWith(AppStateKeys.STEM) == true }
            .associate { checkNotNull(it.key) to it.data }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()

        private const val START_MS = 10_000L
        private const val NETWORK = "network"
        private const val SCREEN = "screen-automatic"
    }
}
