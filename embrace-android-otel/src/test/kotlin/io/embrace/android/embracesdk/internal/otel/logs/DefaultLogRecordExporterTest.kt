package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeReadWriteLogRecord
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.export.ExternalExportDispatcher
import io.embrace.android.embracesdk.internal.otel.export.immediateExportDispatcher
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.state.StateExportMode
import io.embrace.android.embracesdk.internal.otel.state.StateTelemetry
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.state.AppStateKeys
import io.embrace.android.embracesdk.internal.state.StateDefinition
import io.embrace.android.embracesdk.internal.state.StateRecorder
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.logging.data.LogRecordData
import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executor

internal class DefaultLogRecordExporterTest {

    private lateinit var clock: FakeClock
    private lateinit var recorder: StateRecorder
    private lateinit var stateTelemetry: StateTelemetry

    @Before
    fun setUp() {
        clock = FakeClock(10_000L)
        recorder = StateRecorder(clock)
        stateTelemetry = StateTelemetry(recorder, exportMode = { StateExportMode.ASSOCIATION })
    }

    private fun exporter(
        logSink: LogSink,
        externalExporters: List<LogRecordExporter> = emptyList(),
        dispatcher: ExternalExportDispatcher = immediateExportDispatcher(),
        stateTelemetry: StateTelemetry? = null,
    ) = DefaultLogRecordExporter(
        logSink = logSink,
        externalExporters = externalExporters,
        exportCheck = { true },
        externalExportDispatcher = dispatcher,
        stateTelemetry = stateTelemetry,
    )

    private fun declareStates() {
        recorder.declare(StateDefinition("network"), "wifi", false, clock.now())
        recorder.declare(StateDefinition("screen-automatic"), "Initializing", true, clock.now())
    }

    @Test
    fun `export() should store logs in LogSink`() {
        val logSink: LogSink = LogSinkImpl(FakeErrorHandler())
        val data = FakeReadWriteLogRecord()

        runBlocking { exporter(logSink).export(listOf(FakeReadWriteLogRecord())) }

        assertFalse(logSink.logsForNextBatch().isEmpty())
        assertEquals(data.toEmbracePayload(), logSink.logsForNextBatch()[0])
    }

    @Test
    fun `logs reach the sink before exportInline() returns`() {
        val logSink: LogSink = LogSinkImpl(FakeErrorHandler())
        val exporter =
            exporter(logSink, listOf(FakeLogRecordExporter()), ExternalExportDispatcher(FakeErrorHandler()))

        exporter.exportInline(listOf(FakeReadWriteLogRecord()))

        assertEquals(1, logSink.logsForNextBatch().size)
    }

    @Test
    fun `external export runs off the calling thread and is awaited by forceFlush()`() {
        val exportThreads = mutableListOf<String>()
        val threadRecordingExporter = object : LogRecordExporter {
            override suspend fun export(telemetry: List<LogRecordData>): OperationResultCode {
                exportThreads += Thread.currentThread().name
                return OperationResultCode.Success
            }

            override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success
            override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
        }
        val exporter =
            exporter(LogSinkImpl(FakeErrorHandler()), listOf(threadRecordingExporter), ExternalExportDispatcher(FakeErrorHandler()))

        exporter.exportInline(listOf(FakeReadWriteLogRecord()))
        runBlocking { exporter.forceFlush() }

        assertTrue(exportThreads.single().startsWith("emb-otel-export"))
    }

    @Test
    fun `external exporters receive the record as it was when exported`() {
        val pendingExports = ArrayDeque<Runnable>()
        val dispatcher =
            ExternalExportDispatcher(FakeErrorHandler()) { Executor(pendingExports::add).asCoroutineDispatcher() }
        val externalExporter = FakeLogRecordExporter()
        val log = FakeReadWriteLogRecord(body = "original")

        exporter(LogSinkImpl(FakeErrorHandler()), listOf(externalExporter), dispatcher).exportInline(listOf(log))
        log.body = "mutated"
        log.setStringAttribute("late", "value")
        while (pendingExports.isNotEmpty()) {
            pendingExports.removeFirst().run()
        }

        val exported = externalExporter.exportedLogs.single()
        assertEquals("original", exported.body)
        assertFalse(exported.attributes.containsKey("late"))
    }

    @Test
    fun `a throwing external exporter neither stops the others nor fails the internal store`() {
        val logSink: LogSink = LogSinkImpl(FakeErrorHandler())
        val throwingExporter = object : LogRecordExporter {
            override suspend fun export(telemetry: List<LogRecordData>): OperationResultCode =
                throw RuntimeException("boom")

            override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success
            override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
        }
        val workingExporter = FakeLogRecordExporter()

        val result = exporter(logSink, listOf(throwingExporter, workingExporter))
            .exportInline(listOf(FakeReadWriteLogRecord()))

        assertEquals(OperationResultCode.Success, result)
        assertEquals(1, logSink.logsForNextBatch().size)
        assertEquals(1, workingExporter.exportedLogs.size)
    }

    @Test
    fun `nothing is exported when the export check fails`() {
        val logSink: LogSink = LogSinkImpl(FakeErrorHandler())
        val externalExporter = FakeLogRecordExporter()
        val exporter = DefaultLogRecordExporter(
            logSink = logSink,
            externalExporters = listOf(externalExporter),
            exportCheck = { false },
            externalExportDispatcher = immediateExportDispatcher(),
        )

        assertEquals(OperationResultCode.Success, exporter.exportInline(listOf(FakeReadWriteLogRecord())))

        assertTrue(logSink.logsForNextBatch().isEmpty())
        assertTrue(externalExporter.exportedLogs.isEmpty())
    }

    @Test
    fun `an ordinary log is stored with the packed stamp and the number as text and then carries the plain form`() {
        declareStates()
        val logSink: LogSink = LogSinkImpl(FakeErrorHandler())
        val externalExporter = FakeLogRecordExporter()
        val log = FakeReadWriteLogRecord().apply { setStringAttribute("custom", "x") }

        exporter(logSink, listOf(externalExporter), stateTelemetry = stateTelemetry).exportInline(listOf(log))

        assertEquals(
            listOf(
                Attribute("custom", "x"),
                Attribute(AppStateKeys.PACKED, "network=wifi;screen-automatic~Initializing"),
                Attribute(AppStateKeys.SEQ, "2"),
            ),
            logSink.logsForNextBatch().single().attributes,
        )
        val plainForm = mapOf<String, Any>(
            "custom" to "x",
            "${AppStateKeys.VALUE_PREFIX}network" to "wifi",
            "${AppStateKeys.VALUE_PREFIX}screen-automatic" to "Initializing",
            "${AppStateKeys.VALUE_TYPE_PREFIX}screen-automatic" to AppStateKeys.VALUE_TYPE_SYSTEM,
            AppStateKeys.SEQ to 2L,
        )
        assertEquals(plainForm, log.attributes)
        assertEquals(plainForm, externalExporter.exportedLogs.single().attributes)
    }

    @Test
    fun `an app that receives no logs gets no plain form, and Embrace still gets the packed stamp`() {
        declareStates()
        val logSink: LogSink = LogSinkImpl(FakeErrorHandler())
        val externalExporter = FakeLogRecordExporter()
        val log = FakeReadWriteLogRecord().apply { setStringAttribute("custom", "x") }
        val noLogs = StateTelemetry(recorder, exportMode = { StateExportMode.ASSOCIATION }, receivesLogs = { false })

        exporter(logSink, listOf(externalExporter), stateTelemetry = noLogs).exportInline(listOf(log))

        assertEquals(
            listOf(
                Attribute("custom", "x"),
                Attribute(AppStateKeys.PACKED, "network=wifi;screen-automatic~Initializing"),
                Attribute(AppStateKeys.SEQ, "2"),
            ),
            logSink.logsForNextBatch().single().attributes,
        )
        assertEquals(mapOf<String, Any>("custom" to "x"), log.attributes)
        assertEquals(mapOf<String, Any>("custom" to "x"), externalExporter.exportedLogs.single().attributes)
    }

    @Test
    fun `a log emitted before any state exists is stored unchanged`() {
        val logSink: LogSink = LogSinkImpl(FakeErrorHandler())
        val log = FakeReadWriteLogRecord().apply { setStringAttribute("custom", "x") }

        exporter(logSink, stateTelemetry = stateTelemetry).exportInline(listOf(log))

        assertEquals(log.toEmbracePayload(), logSink.logsForNextBatch().single())
        assertEquals(mapOf<String, Any>("custom" to "x"), log.attributes)
    }

    @Test
    fun `exporting externally hands records to the app's exporters and stores nothing for Embrace`() {
        declareStates()
        val logSink: LogSink = LogSinkImpl(FakeErrorHandler())
        val externalExporter = FakeLogRecordExporter()
        val replay = FakeReadWriteLogRecord(eventName = AppStateKeys.CHANGE_EVENT_NAME).apply {
            setStringAttribute(EmbType.State.key, EmbType.State.value)
        }

        exporter(logSink, listOf(externalExporter), stateTelemetry = stateTelemetry).exportExternally(listOf(replay))

        assertTrue(logSink.logsForNextBatch().isEmpty())
        assertEquals(mapOf<String, Any>(EmbType.State.key to EmbType.State.value), externalExporter.exportedLogs.single().attributes)
        assertEquals(mapOf<String, Any>(EmbType.State.key to EmbType.State.value), replay.attributes)
    }

    @Test
    fun `exporting externally does nothing when the export check fails`() {
        val externalExporter = FakeLogRecordExporter()
        val logSink: LogSink = LogSinkImpl(FakeErrorHandler())
        val exporter = DefaultLogRecordExporter(
            logSink = logSink,
            externalExporters = listOf(externalExporter),
            exportCheck = { false },
            externalExportDispatcher = immediateExportDispatcher(),
        )

        exporter.exportExternally(listOf(FakeReadWriteLogRecord()))

        assertTrue(externalExporter.exportedLogs.isEmpty())
        assertTrue(logSink.logsForNextBatch().isEmpty())
    }

    @Test
    fun `an inline record named as a state change is stored like any other log`() {
        declareStates()
        val logSink: LogSink = LogSinkImpl(FakeErrorHandler())
        val externalExporter = FakeLogRecordExporter()
        val log = FakeReadWriteLogRecord(eventName = AppStateKeys.CHANGE_EVENT_NAME, body = "hello").apply {
            setStringAttribute(EmbType.State.key, EmbType.State.value)
        }

        exporter(logSink, listOf(externalExporter), stateTelemetry = stateTelemetry).exportInline(listOf(log))

        val stored = logSink.logsForNextBatch().single()
        assertEquals("hello", stored.body)
        assertEquals(
            listOf(
                Attribute(EmbType.State.key, EmbType.State.value),
                Attribute(AppStateKeys.PACKED, "network=wifi;screen-automatic~Initializing"),
                Attribute(AppStateKeys.SEQ, "2"),
            ),
            stored.attributes,
        )
        assertEquals(1, externalExporter.exportedLogs.size)
    }

    @Test
    fun `a record that cannot be written to is reported as skipped and is still stored and exported`() {
        declareStates()
        var skipped = 0
        val telemetry = StateTelemetry(
            recorder,
            exportMode = { StateExportMode.ASSOCIATION },
            onPlainStampSkipped = { skipped++ },
        )
        val logSink: LogSink = LogSinkImpl(FakeErrorHandler())
        val externalExporter = FakeLogRecordExporter()
        val writable = FakeReadWriteLogRecord(body = "hello").apply { setStringAttribute("custom", "x") }
        val readOnly = object : LogRecordData by writable {}

        val result = exporter(logSink, listOf(externalExporter), stateTelemetry = telemetry).exportInline(listOf(readOnly))

        assertEquals(OperationResultCode.Success, result)
        assertEquals(1, skipped)
        assertEquals(
            listOf(
                Attribute("custom", "x"),
                Attribute(AppStateKeys.PACKED, "network=wifi;screen-automatic~Initializing"),
                Attribute(AppStateKeys.SEQ, "2"),
            ),
            logSink.logsForNextBatch().single().attributes,
        )
        assertEquals(mapOf<String, Any>("custom" to "x"), externalExporter.exportedLogs.single().attributes)
    }

    @Test
    fun `a native crash and an exit log are never stamped with the current state`() {
        declareStates()
        recorder.update("network", "cell", false, clock.now())
        listOf(EmbType.System.NativeCrash, EmbType.System.Exit).forEach { type ->
            val logSink: LogSink = LogSinkImpl(FakeErrorHandler())
            val log = FakeReadWriteLogRecord().apply { setStringAttribute(type.key, type.value) }

            exporter(logSink, stateTelemetry = stateTelemetry).exportInline(listOf(log))

            assertEquals(listOf(Attribute(type.key, type.value)), logSink.logsForNextBatch().single().attributes)
            assertEquals(mapOf<String, Any>(type.key to type.value), log.attributes)
        }
    }

    @Test
    fun `plain state attributes of a dead process are packed for Embrace and its number becomes a long`() {
        declareStates()
        recorder.update("network", "cell", false, clock.now())
        val logSink: LogSink = LogSinkImpl(FakeErrorHandler())
        val log = FakeReadWriteLogRecord().apply {
            setStringAttribute(EmbType.System.NativeCrash.key, EmbType.System.NativeCrash.value)
            setStringAttribute("${AppStateKeys.VALUE_PREFIX}network", "none")
            setStringAttribute("${AppStateKeys.VALUE_PREFIX}screen-automatic", "Home")
            setStringAttribute("${AppStateKeys.VALUE_TYPE_PREFIX}screen-automatic", AppStateKeys.VALUE_TYPE_SYSTEM)
            setStringAttribute(AppStateKeys.SEQ, "41")
        }

        exporter(logSink, stateTelemetry = stateTelemetry).exportInline(listOf(log))

        assertEquals(
            listOf(
                Attribute(EmbType.System.NativeCrash.key, EmbType.System.NativeCrash.value),
                Attribute(AppStateKeys.SEQ, "41"),
                Attribute(AppStateKeys.PACKED, "network=none;screen-automatic~Home"),
            ),
            logSink.logsForNextBatch().single().attributes,
        )
        assertEquals(41L, log.attributes[AppStateKeys.SEQ])
    }

    @Test
    fun `without state telemetry a log with state attributes is stored as it is`() {
        val logSink: LogSink = LogSinkImpl(FakeErrorHandler())
        val log = FakeReadWriteLogRecord().apply {
            setStringAttribute("${AppStateKeys.VALUE_PREFIX}network", "none")
            setStringAttribute(AppStateKeys.SEQ, "41")
        }

        exporter(logSink).exportInline(listOf(log))

        assertEquals(log.toEmbracePayload(), logSink.logsForNextBatch().single())
        assertEquals("41", log.attributes[AppStateKeys.SEQ])
    }
}
