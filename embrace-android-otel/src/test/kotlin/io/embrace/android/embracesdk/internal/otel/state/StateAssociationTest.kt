package io.embrace.android.embracesdk.internal.otel.state

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeInternalTelemetryService
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeLogRecordProcessor
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.fakes.FakeSpanProcessor
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.TestUuidSource
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.export.immediateExportDispatcher
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
import io.embrace.android.embracesdk.internal.otel.sdk.OtelSdkWrapper
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanFactoryImpl
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.spans.SpanServiceImpl
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.logging.SeverityNumber
import io.opentelemetry.kotlin.logging.data.LogRecordData
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.export.SpanProcessor
import io.opentelemetry.kotlin.tracing.model.ReadableSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Telemetry associated with state, through the SDK's own OpenTelemetry pipeline and an app's exporters and processors.
 */
@RunWith(Parameterized::class)
internal class StateAssociationTest(
    private val otelSdkMode: OtelSdkMode,
) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()

        private const val SESSION_ATTRIBUTE = "emb.session_part_id"
        private val WIFI = mapOf("network" to "wifi", "screen" to "~Initializing")
        private const val MANY_STATES = 500
        private const val LONG_VALUE = 2_000
        private const val UNCAPPED_VALUE = 5_000
        private const val MAX_LOG_ATTRIBUTES = 400
        private val OFFLINE = mapOf("network" to "none", "screen" to "~Initializing")
    }

    private lateinit var clock: FakeClock
    private lateinit var spanRepository: SpanRepository
    private lateinit var logSink: LogSinkImpl
    private lateinit var spanExporter: FakeSpanExporter
    private lateinit var logExporter: FakeLogRecordExporter
    private lateinit var endedSpans: MutableList<ReadableSpan>
    private lateinit var startedSpanAttributes: MutableMap<String, Map<String, Any>>
    private lateinit var processedLogs: MutableList<LogRecordData>
    private lateinit var configuration: OtelSdkConfig
    private lateinit var sdk: OtelSdkWrapper

    /**
     * Every stamp there has been that is still remembered, oldest first. The last is the current one.
     */
    private var remembered: List<StateStamp> = emptyList()

    @Volatile
    private var state: StateStamp? = null

    @Before
    fun setup() {
        clock = FakeClock()
        spanRepository = SpanRepository(FakeErrorHandler())
        logSink = LogSinkImpl(FakeErrorHandler())
        change(FakeStateStamp(3, WIFI))
    }

    @Test
    fun `with the values exported, an app's exporter and processor see them on a span, and Embrace's payload has the link alone`() {
        createSdk(valuesOnExportedSpans = true, exportsEvents = true)
        runSpanAcrossAChange()

        val expected = mapOf(
            StateAttributes.SEQUENCE to "3",
            StateAttributes.END_SEQUENCE to "4",
            "emb.states.value.network" to "wifi",
            "emb.states.value.screen" to "Initializing",
            "emb.states.value_type.screen" to "system",
            "emb.states.end_value.network" to "none",
        )
        assertEquals(expected, spanExporter.exportedSpans.single().stateAttributes())
        assertEquals(expected, endedSpans.single().stateAttributes())
        assertEquals(expected, endedSpans.single().toSpanData().stateAttributes())
        // when it started, the processor saw the values it started with
        assertEquals(
            expected - StateAttributes.END_SEQUENCE - "emb.states.end_value.network",
            checkNotNull(startedSpanAttributes["across"]).stateAttributes(),
        )
        assertEquals(
            mapOf(StateAttributes.SEQUENCE to "3", StateAttributes.END_SEQUENCE to "4"),
            spanRepository.completedOtelSpans().single().stateAttributes(),
        )
        assertTrue(configuration.spanProcessor.isOnEndingRequired())
    }

    @Test
    fun `with the values not exported, an app's exporter and processor see the link alone, as Embrace's payload does`() {
        createSdk(valuesOnExportedSpans = false, exportsEvents = true)
        runSpanAcrossAChange()

        val expected = mapOf(StateAttributes.SEQUENCE to "3", StateAttributes.END_SEQUENCE to "4")
        assertEquals(expected, spanExporter.exportedSpans.single().stateAttributes())
        assertEquals(expected, endedSpans.single().stateAttributes())
        assertEquals(expected, spanRepository.completedOtelSpans().single().stateAttributes())
    }

    @Test
    fun `a span given a start time from before the last change carries the number that held then, if that is remembered`() {
        createSdk(valuesOnExportedSpans = true, exportsEvents = true)
        val changeMs = clock.tick(1000)
        change(FakeStateStamp(4, OFFLINE, sinceMs = changeMs))
        clock.tick(1000)
        sdk.sdkTracer.startSpan("earlier", startTimestamp = (changeMs - 1).millisToNanos()).end()

        val link = mapOf(StateAttributes.SEQUENCE to "3", StateAttributes.END_SEQUENCE to "4")
        val values = mapOf(
            "emb.states.value.network" to "wifi",
            "emb.states.value.screen" to "Initializing",
            "emb.states.value_type.screen" to "system",
            "emb.states.end_value.network" to "none",
        )
        assertEquals(link + values, exported("earlier").stateAttributes())
        assertEquals(link + values, endedSpans.single().stateAttributes())
        assertEquals(link, completed("earlier").stateAttributes())

        // once the stamp that held at that time is forgotten, such a span has no link and no values, wherever it goes
        remembered = remembered.takeLast(1)
        sdk.sdkTracer.startSpan("not-remembered", startTimestamp = (changeMs - 1).millisToNanos()).end()
        assertEquals(emptyMap<String, String>(), exported("not-remembered").stateAttributes())
        assertEquals(emptyMap<String, String>(), endedSpans.last().stateAttributes())
        assertEquals(emptyMap<String, String>(), completed("not-remembered").stateAttributes())
        assertEquals(0, configuration.trackedStateSpans())
    }

    @Test
    fun `a span the span service records once it is over, or stops at a past time, carries the numbers that held at its times`() {
        createSdk(valuesOnExportedSpans = true, exportsEvents = true)
        val spanService = createSpanService()
        val stoppedEarlier = spanService.startSpan("stopped-earlier", internal = false)
        val changeMs = clock.tick(1000)
        change(FakeStateStamp(4, OFFLINE, sinceMs = changeMs))
        clock.tick(1000)

        // it ended before the change, though it is only told so after it
        assertTrue(stoppedEarlier.stop(endTimeMs = changeMs - 100))
        assertTrue(spanService.recordCompletedSpan("across-the-change", changeMs - 500, changeMs + 500, internal = false))
        assertTrue(spanService.recordCompletedSpan("after-the-change", changeMs + 100, changeMs + 500, internal = false))
        remembered = remembered.takeLast(1)
        assertTrue(spanService.recordCompletedSpan("not-remembered", changeMs - 500, changeMs + 500, internal = false))

        assertEquals(mapOf(StateAttributes.SEQUENCE to "3"), completed("stopped-earlier").stateAttributes())
        assertEquals(
            mapOf(
                StateAttributes.SEQUENCE to "3",
                "emb.states.value.network" to "wifi",
                "emb.states.value.screen" to "Initializing",
                "emb.states.value_type.screen" to "system",
            ),
            exported("stopped-earlier").stateAttributes(),
        )

        assertEquals(
            mapOf(StateAttributes.SEQUENCE to "3", StateAttributes.END_SEQUENCE to "4"),
            completed("across-the-change").stateAttributes(),
        )
        assertEquals("wifi", exported("across-the-change").attributes["emb.states.value.network"])
        assertEquals("none", exported("across-the-change").attributes["emb.states.end_value.network"])

        assertEquals(mapOf(StateAttributes.SEQUENCE to "4"), completed("after-the-change").stateAttributes())
        assertEquals("none", exported("after-the-change").attributes["emb.states.value.network"])

        assertEquals(emptyMap<String, String>(), completed("not-remembered").stateAttributes())
        assertEquals(emptyMap<String, String>(), exported("not-remembered").stateAttributes())
        assertEquals(0, configuration.trackedStateSpans())
    }

    @Test
    fun `a span in progress carries the number it started with in its snapshot, and the number is still whole when it ends`() {
        createSdk(valuesOnExportedSpans = false, exportsEvents = false)
        val spanService = createSpanService()
        val span = spanService.startSpan("in-progress", internal = false)
        val changeMs = clock.tick(1000)
        change(FakeStateStamp(4, OFFLINE, sinceMs = changeMs))
        remembered = remembered.takeLast(1)
        val unlinked = spanService.startSpan("unlinked", startTimeMs = changeMs - 1, internal = false)

        // a snapshot says nothing of the end of a span, which it has not had
        assertEquals(mapOf(StateAttributes.SEQUENCE to "3"), checkNotNull(span.snapshot()).stateAttributes())
        assertEquals(emptyMap<String, String>(), checkNotNull(unlinked.snapshot()).stateAttributes())

        assertTrue(span.stop())
        assertTrue(unlinked.stop())
        with(exported("in-progress").attributes) {
            assertEquals(3L, get(StateAttributes.SEQUENCE))
            assertEquals(4L, get(StateAttributes.END_SEQUENCE))
        }
        assertEquals(
            mapOf(StateAttributes.SEQUENCE to "3", StateAttributes.END_SEQUENCE to "4"),
            completed("in-progress").stateAttributes(),
        )
        assertEquals(emptyMap<String, String>(), exported("unlinked").stateAttributes())
    }

    @Test
    fun `with no association, no span carries anything of state and nothing is asked of a span as it ends`() {
        createSdk(associated = false)
        val spanService = createSpanService()
        val span = spanService.startSpan("span", internal = false)
        change(FakeStateStamp(4, OFFLINE))
        assertEquals(emptyMap<String, String>(), checkNotNull(span.snapshot()).stateAttributes())
        assertTrue(span.stop())
        assertTrue(spanService.recordCompletedSpan("recorded", clock.now() - 500, clock.now() - 100, internal = false))
        sdk.sdkTracer.startSpan("direct").end()

        assertEquals(3, spanExporter.exportedSpans.size)
        spanExporter.exportedSpans.forEach { assertEquals(emptyMap<String, String>(), it.stateAttributes()) }
        endedSpans.forEach { assertEquals(emptyMap<String, String>(), it.stateAttributes()) }
        spanRepository.completedOtelSpans().forEach { assertEquals(emptyMap<String, String>(), it.stateAttributes()) }
        assertFalse(configuration.spanProcessor.isOnEndingRequired())
        assertNull(configuration.spanStateLink)
    }

    @Test
    fun `with the values exported, nothing stays kept for a span an app never sees or its processor fails on`() {
        val failing = FakeSpanProcessor(
            onStartAction = { error("an app's processor failed as a span started") },
            onEndAction = { error("an app's processor failed as a span ended") },
        )
        createSdk(valuesOnExportedSpans = true, exportsEvents = true, appSpanProcessor = failing)
        val spanService = createSpanService()

        val seen = spanService.startSpan("seen-by-a-failing-processor", internal = false)
        val hidden = spanService.startSpan("private", private = true)
        val discarded = spanService.startSpan("discarded", internal = false)
        assertEquals(3, configuration.trackedStateSpans())
        change(FakeStateStamp(4, OFFLINE))
        assertTrue(seen.stop())
        assertTrue(hidden.stop())
        assertTrue(discarded.discard())
        assertEquals(0, configuration.trackedStateSpans())
        assertEquals(listOf("seen-by-a-failing-processor"), spanExporter.exportedSpans.map { it.name })
        assertEquals(listOf("seen-by-a-failing-processor"), failing.endedSpanNames)

        // one that starts before export is disabled and ends after it, and one that starts after it
        val endsUnexported = spanService.startSpan("ends-unexported", internal = false)
        configuration.disableDataExport()
        val unexported = spanService.startSpan("unexported", internal = false)
        assertEquals(2, configuration.trackedStateSpans())
        change(FakeStateStamp(5, WIFI))
        assertTrue(endsUnexported.stop())
        assertTrue(unexported.stop())
        assertEquals(0, configuration.trackedStateSpans())
        assertEquals(1, spanExporter.exportedSpans.size)
    }

    @Test
    fun `an event that describes state reaches an app's exporter and processor as it was emitted, and not Embrace's payload`() {
        createSdk(valuesOnExportedSpans = false, exportsEvents = true)
        val eventTimeNanos = (clock.now() - 5).millisToNanos()
        StateEventLogger({ sdk.sdkLogger }, { sdk.openTelemetryKotlin.context.root() }).emit(
            eventName = StateAttributes.CHANGE_EVENT,
            timestampNanos = eventTimeNanos,
        ) {
            setStringAttribute(StateAttributes.NAME, "network")
            setLongAttribute(StateAttributes.SEQUENCE, 4)
        }
        sdk.sdkLogger.emit(body = "a log")

        // the event is not in Embrace's payload, and the log emitted after it is
        assertEquals(listOf("a log"), logSink.logsForNextBatch().map { it.body })

        listOf(logExporter.exportedLogs.first(), processedLogs.first()).forEach { event ->
            assertEquals(StateAttributes.CHANGE_EVENT, event.eventName)
            assertNull(event.body)
            assertEquals(SeverityNumber.INFO, event.severityNumber)
            assertEquals(eventTimeNanos, event.timestamp)
            // nothing is stamped on it: not the session, not an id, and not the value of every state now
            assertEquals(
                mapOf(StateAttributes.NAME to "network", StateAttributes.SEQUENCE to "4"),
                event.attributes.mapValues { it.value.toString() },
            )
            assertEquals("pid", event.resource.attributes[EmbSessionAttributes.EMB_PROCESS_IDENTIFIER])
        }
        with(logExporter.exportedLogs.last()) {
            assertEquals("a log", body)
            assertEquals("part", attributes[SESSION_ATTRIBUTE])
        }
    }

    @Test
    fun `the Resource does not say which process it is unless events that describe state are exported`() {
        createSdk(valuesOnExportedSpans = false, exportsEvents = false)
        sdk.sdkTracer.startSpan("span").end()
        sdk.sdkLogger.emit(body = "a log")

        with(spanExporter.exportedSpans.single()) {
            assertFalse(resource.attributes.containsKey(EmbSessionAttributes.EMB_PROCESS_IDENTIFIER))
            assertEquals(mapOf(StateAttributes.SEQUENCE to "3"), stateAttributes())
        }
        assertFalse(logExporter.exportedLogs.single().resource.attributes.containsKey(EmbSessionAttributes.EMB_PROCESS_IDENTIFIER))
    }

    @Test
    fun `a log carries the packed stamp into Embrace's payload and the plain one to an app's exporter and processor`() {
        createSdk(exportsEvents = true)
        sdk.sdkLogger.emit(body = "a log")

        val payload = logSink.logsForNextBatch().single().attributes.orEmpty().associate { it.key to it.data }
        assertEquals("network=wifi;screen~Initializing", payload["emb.states.packed"])
        assertEquals("3", payload["emb.states.seq"])
        assertEquals(emptyList<String>(), payload.keys.filter { it?.startsWith("emb.states.value") == true })

        val plain = mapOf(
            "emb.states.value.network" to "wifi",
            "emb.states.value.screen" to "Initializing",
            "emb.states.value_type.screen" to "system",
        )
        listOf(logExporter.exportedLogs.single(), processedLogs.single()).forEach { log ->
            assertEquals(plain, log.attributes.filterKeys { it.startsWith("emb.states.value") })
            // a whole number, where the payload's is text
            assertEquals(3L, log.attributes["emb.states.seq"])
            assertFalse(log.attributes.containsKey("emb.states.packed"))
            assertEquals("part", log.attributes[SESSION_ATTRIBUTE])
        }
    }

    @Test
    fun `an exit record about the last process carries none of this process's state, and an ordinary log carries both forms`() {
        createSdk(exportsEvents = true)
        sdk.sdkLogger.emit(
            body = "exit",
            attributes = {
                setStringAttribute("emb.type", "sys.exit")
                // the exit record names its session part blank, as the process it is about is not this one
                setStringAttribute(SESSION_ATTRIBUTE, "")
            },
        )
        sdk.sdkLogger.emit(body = "ordinary")

        val stored = logSink.logsForNextBatch().associate {
            it.body to it.attributes.orEmpty().mapNotNull { a -> a.key }.toSet()
        }
        val exited = checkNotNull(stored["exit"])
        assertEquals(emptyList<String>(), exited.filter { it.startsWith("emb.states") || it == "emb.states.packed" })
        val ordinary = checkNotNull(stored["ordinary"])
        assertTrue(ordinary.containsAll(listOf("emb.states.packed", "emb.states.seq")))

        val handed = logExporter.exportedLogs.associateBy { it.body }
        assertEquals(emptyList<String>(), checkNotNull(handed["exit"]).attributes.keys.filter { it.startsWith("emb.states") })
        assertTrue(checkNotNull(handed["ordinary"]).attributes.containsKey("emb.states.value.network"))
        // the rest of the metadata is still stamped on the exit record as before
        assertEquals("", handed.getValue("exit").attributes[SESSION_ATTRIBUTE])
    }

    @Test
    fun `with no processor or exporter of the app's for logs, Embrace's payload has the packed stamp and no plain one is built`() {
        val stamp = CountingStateStamp(FakeStateStamp(3, WIFI))
        change(stamp)
        createSdk(exportsEvents = true, appLogReceivers = false)
        sdk.sdkLogger.emit(body = "a log")

        val payload = logSink.logsForNextBatch().single().attributes.orEmpty().associate { it.key to it.data }
        assertEquals("network=wifi;screen~Initializing", payload["emb.states.packed"])
        assertEquals("3", payload["emb.states.seq"])
        assertEquals(0, stamp.attributesBuilt)
        assertEquals(emptyList<String>(), payload.keys.filter { it?.startsWith("emb.states.value") == true })
    }

    @Test
    fun `many long states reach Embrace's payload whole in the packed stamp, which no limit on a log attribute cuts`() {
        val values = mapOf("longest" to "w".repeat(UNCAPPED_VALUE)) + List(MANY_STATES) {
            "state-$it"
        }.associateWith { "v".repeat(LONG_VALUE) }
        val stamp = FakeStateStamp(5, values)
        change(stamp)
        createSdk(exportsEvents = true)
        sdk.sdkLogger.emit(body = "a log")

        // the packed attribute is added to the payload as it is stored, past the limits on a log record's attributes
        val packed = logSink.logsForNextBatch().single().attributes.orEmpty().single { it.key == "emb.states.packed" }.data
        assertEquals(stamp.encoded, packed)
        assertTrue(checkNotNull(packed).startsWith("longest=" + "w".repeat(UNCAPPED_VALUE) + ";state-0="))
    }

    @Test
    fun `the plain stamp on what an app is handed is held to the limit on a log record's attributes`() {
        val values = mapOf("longest" to "w".repeat(UNCAPPED_VALUE)) + List(MANY_STATES) {
            "state-$it"
        }.associateWith { "v".repeat(LONG_VALUE) }
        change(FakeStateStamp(5, values))
        createSdk(exportsEvents = true)
        sdk.sdkLogger.emit(body = "a log")

        val handed = logExporter.exportedLogs.single().attributes
        val plain = handed.filterKeys { it.startsWith("emb.states.value.") }
        // the OpenTelemetry SDK keeps at most the configured number of attributes, 400, and drops the rest without saying so:
        // the log's own attributes and the stamp's share it, and the stamp comes after them
        assertTrue(handed.size <= MAX_LOG_ATTRIBUTES)
        // the number is written first, so it is not what the limit drops
        assertEquals(5L, handed["emb.states.seq"])
        assertTrue("${plain.size}", plain.size < MANY_STATES)
        // a value is not cut: no limit on the length of a log record's attribute is configured
        assertEquals(UNCAPPED_VALUE, plain["emb.states.value.longest"]?.toString()?.length)
    }

    private fun runSpanAcrossAChange() {
        val span = sdk.sdkTracer.startSpan("across")
        change(FakeStateStamp(4, OFFLINE))
        span.end()
        assertEquals("pid", spanExporter.exportedSpans.single().resource.attributes[EmbSessionAttributes.EMB_PROCESS_IDENTIFIER])
    }

    /**
     * Makes [stamp] the current one, and remembers it.
     */
    private fun change(stamp: StateStamp) {
        remembered = remembered + stamp
        state = stamp
    }

    private fun createSdk(
        valuesOnExportedSpans: Boolean = false,
        exportsEvents: Boolean = false,
        associated: Boolean = true,
        appLogReceivers: Boolean = true,
        appSpanProcessor: SpanProcessor = FakeSpanProcessor(
            onStartAction = { startedSpanAttributes[it.name] = it.attributes.toMap() },
            onEndAction = { endedSpans.add(it) },
        ),
    ) {
        spanExporter = FakeSpanExporter()
        logExporter = FakeLogRecordExporter()
        endedSpans = mutableListOf()
        startedSpanAttributes = mutableMapOf()
        processedLogs = mutableListOf()
        configuration = OtelSdkConfig(
            spanRepository = spanRepository,
            logSink = logSink,
            sdkName = "sdk",
            sdkVersion = "1.0",
            appVersion = "2.5.1",
            packageName = "com.test.app",
            systemInfo = SystemInfo(),
            uuidSource = TestUuidSource(),
            eventMetadataProvider = { mapOf(SESSION_ATTRIBUTE to "part") },
            processIdentifierProvider = { "pid" },
            externalExportDispatcher = immediateExportDispatcher(),
            stateAssociation = if (associated) {
                StateAssociation(
                    stateProvider = { state },
                    valuesOnExportedSpans = valuesOnExportedSpans,
                    exportsEvents = exportsEvents,
                    stateAt = { timeMs -> remembered.lastOrNull { it.sinceMs <= timeMs } },
                )
            } else {
                null
            },
        )
        with(configuration.externalConfig) {
            addSpanExporter(spanExporter)
            addSpanProcessor(appSpanProcessor)
            if (appLogReceivers) {
                addLogExporter(logExporter)
                addLogRecordProcessor(FakeLogRecordProcessor { processedLogs.add(it.toLogRecordData()) })
            }
        }
        sdk = OtelSdkWrapper(
            otelClock = FakeOtelKotlinClock(clock),
            configuration = configuration,
            spanServiceProvider = ::FakeSpanService,
            errorHandler = FakeErrorHandler(),
            useKotlinSdk = otelSdkMode.useKotlinSdk,
        )
    }

    private fun createSpanService(): SpanServiceImpl {
        val dataValidator = DataValidator(telemetryService = FakeInternalTelemetryService())
        return SpanServiceImpl(
            spanRepository = spanRepository,
            dataValidator = dataValidator,
            canStartNewSpan = { _, _, _ -> true },
            embraceSpanFactory = EmbraceSpanFactoryImpl(
                openTelemetryClock = FakeOtelKotlinClock(clock),
                spanRepository = spanRepository,
                dataValidator = dataValidator,
                telemetryService = FakeInternalTelemetryService(),
                errorHandler = FakeErrorHandler(),
                stateLink = configuration.spanStateLink,
            ),
            tracer = sdk.sdkTracer,
            openTelemetry = sdk.openTelemetryKotlin,
        )
    }

    private fun exported(name: String): SpanData = spanExporter.exportedSpans.single { it.name == name }

    private fun completed(name: String): Span = spanRepository.completedOtelSpans().single { it.name == name }

    private fun SpanData.stateAttributes(): Map<String, String> = attributes.stateAttributes()

    private fun Span.stateAttributes(): Map<String, String> = checkNotNull(attributes).associate { attribute: Attribute ->
        checkNotNull(attribute.key) to checkNotNull(attribute.data)
    }.stateAttributes()

    private fun Map<String, Any>.stateAttributes(): Map<String, String> =
        filterKeys { it.startsWith("emb.states.") }.mapValues { it.value.toString() }
}
