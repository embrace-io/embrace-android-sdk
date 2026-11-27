package io.embrace.android.embracesdk.internal.otel.state

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeInternalTelemetryService
import io.embrace.android.embracesdk.fakes.FakeLogRecord
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryLogger
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.fakeOpenTelemetry
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.state.AppStateKeys
import io.embrace.android.embracesdk.internal.state.StateChange
import io.embrace.android.embracesdk.internal.state.StateDefinition
import io.embrace.android.embracesdk.internal.state.StateRecorder
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.opentelemetry.kotlin.logging.Logger
import io.opentelemetry.kotlin.logging.SeverityNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class StateReplayEmitterTest {

    private lateinit var clock: FakeClock
    private lateinit var executor: BlockingScheduledExecutorService
    private lateinit var logger: FakeOpenTelemetryLogger
    private lateinit var errorHandler: FakeErrorHandler
    private lateinit var telemetryService: FakeInternalTelemetryService
    private lateinit var recorder: StateRecorder

    @Before
    fun setUp() {
        clock = FakeClock(START_MS)
        executor = BlockingScheduledExecutorService(clock, blockingMode = true)
        logger = FakeOpenTelemetryLogger()
        errorHandler = FakeErrorHandler(throwOnSdkError = false)
        telemetryService = FakeInternalTelemetryService()
        recorder = StateRecorder(clock)
    }

    @Test
    fun `a change makes one record with the change and no values in the association rendering`() {
        attach(StateExportMode.ASSOCIATION)
        recorder.declare(StateDefinition(NETWORK, attributeKeys = setOf("reason")), "wifi", false, START_MS)
        recorder.openPart(PART, START_MS)
        executor.runCurrentlyBlocked()
        logger.logs.clear()

        recorder.update(NETWORK, "cell", false, CHANGE_MS, mapOf("reason" to "lost", "dropped" to "x"))
        assertEquals(0, logger.logs.size)
        executor.runCurrentlyBlocked()

        val record = logger.logs.single()
        assertEquals(AppStateKeys.CHANGE_EVENT_NAME, record.eventName)
        assertEquals(CHANGE_MS.millisToNanos(), record.timestamp)
        assertEquals(CHANGE_MS.millisToNanos(), record.observedTimestamp)
        assertEquals(SeverityNumber.INFO, record.severityNumber)
        assertEquals("INFO", record.severityText)
        assertNull(record.body)
        assertEquals(
            mapOf<String, Any>(
                "reason" to "lost",
                EmbType.State.key to EmbType.State.value,
                AppStateKeys.SEQ to 2L,
                AppStateKeys.ENTITY_TYPE to AppStateKeys.ENTITY_TYPE_PROCESS,
                AppStateKeys.ENTITY_ID to PROCESS_ID,
                AppStateKeys.NAME to NETWORK,
                AppStateKeys.NEW_VALUE to "cell",
                AppStateKeys.PREVIOUS_VALUE to "wifi",
            ),
            record.attributes,
        )
    }

    @Test
    fun `the verbose rendering also carries every state's value after the change`() {
        attach(StateExportMode.VERBOSE)
        recorder.declare(StateDefinition(NETWORK), "wifi", false, START_MS)
        recorder.declare(StateDefinition(SCREEN), "Initializing", true, START_MS)
        recorder.openPart(PART, START_MS)
        executor.runCurrentlyBlocked()
        logger.logs.clear()

        recorder.update(NETWORK, "cell", false, CHANGE_MS)
        executor.runCurrentlyBlocked()

        assertEquals(
            mapOf<String, Any>(
                EmbType.State.key to EmbType.State.value,
                AppStateKeys.SEQ to 3L,
                AppStateKeys.ENTITY_TYPE to AppStateKeys.ENTITY_TYPE_PROCESS,
                AppStateKeys.ENTITY_ID to PROCESS_ID,
                AppStateKeys.NAME to NETWORK,
                AppStateKeys.NEW_VALUE to "cell",
                AppStateKeys.PREVIOUS_VALUE to "wifi",
                "${AppStateKeys.VALUE_PREFIX}$NETWORK" to "cell",
                "${AppStateKeys.VALUE_PREFIX}$SCREEN" to "Initializing",
                "${AppStateKeys.VALUE_TYPE_PREFIX}$SCREEN" to AppStateKeys.VALUE_TYPE_SYSTEM,
            ),
            logger.logs.single().attributes,
        )
    }

    @Test
    fun `a generated value and a generated previous value carry their types and a first change has no previous value`() {
        attach(StateExportMode.ASSOCIATION)
        recorder.openPart(PART, START_MS)
        recorder.declare(StateDefinition(SCREEN), "Initializing", true, CHANGE_MS)
        recorder.update(SCREEN, "Home", false, CHANGE_MS + 1)
        recorder.update(SCREEN, "Done", true, CHANGE_MS + 2)
        executor.runCurrentlyBlocked()

        val changes = logger.logs.filter { it.eventName == AppStateKeys.CHANGE_EVENT_NAME }
        assertEquals(3, changes.size)
        assertEquals(
            mapOf<String, Any>(
                EmbType.State.key to EmbType.State.value,
                AppStateKeys.SEQ to 1L,
                AppStateKeys.ENTITY_TYPE to AppStateKeys.ENTITY_TYPE_PROCESS,
                AppStateKeys.ENTITY_ID to PROCESS_ID,
                AppStateKeys.NAME to SCREEN,
                AppStateKeys.NEW_VALUE to "Initializing",
                AppStateKeys.NEW_VALUE_TYPE to AppStateKeys.VALUE_TYPE_SYSTEM,
            ),
            changes[0].attributes,
        )
        assertEquals("Home", changes[1].attributes[AppStateKeys.NEW_VALUE])
        assertNull(changes[1].attributes[AppStateKeys.NEW_VALUE_TYPE])
        assertEquals("Initializing", changes[1].attributes[AppStateKeys.PREVIOUS_VALUE])
        assertEquals(AppStateKeys.VALUE_TYPE_SYSTEM, changes[1].attributes[AppStateKeys.PREVIOUS_VALUE_TYPE])
        assertEquals("Done", changes[2].attributes[AppStateKeys.NEW_VALUE])
        assertEquals(AppStateKeys.VALUE_TYPE_SYSTEM, changes[2].attributes[AppStateKeys.NEW_VALUE_TYPE])
        assertEquals("Home", changes[2].attributes[AppStateKeys.PREVIOUS_VALUE])
        assertNull(changes[2].attributes[AppStateKeys.PREVIOUS_VALUE_TYPE])
    }

    @Test
    fun `a part opening makes a snapshot record with every state's value in either rendering`() {
        StateExportMode.entries.forEach { mode ->
            logger.logs.clear()
            recorder = StateRecorder(clock)
            attach(mode)
            recorder.declare(StateDefinition(NETWORK), "wifi", false, START_MS)
            recorder.declare(StateDefinition(SCREEN), "Initializing", true, START_MS)
            recorder.openPart(PART, PART_MS)
            executor.runCurrentlyBlocked()

            val record = logger.logs.single()
            assertEquals(AppStateKeys.SNAPSHOT_EVENT_NAME, record.eventName)
            assertEquals(PART_MS.millisToNanos(), record.timestamp)
            assertEquals(SeverityNumber.INFO, record.severityNumber)
            assertEquals(
                mapOf<String, Any>(
                    EmbType.State.key to EmbType.State.value,
                    AppStateKeys.SEQ to 2L,
                    AppStateKeys.ENTITY_TYPE to AppStateKeys.ENTITY_TYPE_PROCESS,
                    AppStateKeys.ENTITY_ID to PROCESS_ID,
                    "${AppStateKeys.VALUE_PREFIX}$NETWORK" to "wifi",
                    "${AppStateKeys.VALUE_PREFIX}$SCREEN" to "Initializing",
                    "${AppStateKeys.VALUE_TYPE_PREFIX}$SCREEN" to AppStateKeys.VALUE_TYPE_SYSTEM,
                ),
                record.attributes,
            )
        }
    }

    @Test
    fun `a change's own attributes cannot override an attribute the SDK sets`() {
        val emitter = attach(StateExportMode.ASSOCIATION)
        recorder.declare(StateDefinition(NETWORK), "wifi", false, START_MS)
        val after = recorder.current()
        val hostile = mapOf(
            EmbType.State.key to "performance",
            AppStateKeys.SEQ to "999",
            AppStateKeys.ENTITY_TYPE to "host",
            AppStateKeys.ENTITY_ID to "other",
            AppStateKeys.NAME to "other",
            AppStateKeys.NEW_VALUE to "other",
            AppStateKeys.PREVIOUS_VALUE to "other",
            "harmless" to "kept",
        )

        emitter.onChange(
            StateChange(
                seq = 7L,
                definition = StateDefinition(NETWORK),
                timeMs = CHANGE_MS,
                value = "cell",
                system = false,
                previousValue = "wifi",
                previousSystem = false,
                attributes = hostile,
                after = after,
            ),
        )
        executor.runCurrentlyBlocked()

        assertEquals(
            mapOf<String, Any>(
                "harmless" to "kept",
                EmbType.State.key to EmbType.State.value,
                AppStateKeys.SEQ to 7L,
                AppStateKeys.ENTITY_TYPE to AppStateKeys.ENTITY_TYPE_PROCESS,
                AppStateKeys.ENTITY_ID to PROCESS_ID,
                AppStateKeys.NAME to NETWORK,
                AppStateKeys.NEW_VALUE to "cell",
                AppStateKeys.PREVIOUS_VALUE to "wifi",
            ),
            logger.logs.single().attributes,
        )
    }

    @Test
    fun `events past the queue cap are left out and counted, and a later one is queued again`() {
        attach(StateExportMode.ASSOCIATION, maxQueued = 2)
        recorder.declare(StateDefinition(NETWORK), "wifi", false, START_MS)
        recorder.openPart(PART, START_MS) // queued: snapshot
        recorder.update(NETWORK, "cell", false, CHANGE_MS) // queued: change 2
        recorder.update(NETWORK, "none", false, CHANGE_MS + 1) // left out
        recorder.update(NETWORK, "wifi", false, CHANGE_MS + 2) // left out

        assertEquals(0, logger.logs.size)
        assertEquals(
            listOf(
                LIMIT_TYPE to AppliedLimitType.DROP,
                LIMIT_TYPE to AppliedLimitType.DROP,
            ),
            telemetryService.appliedLimits,
        )
        executor.runCurrentlyBlocked()

        assertEquals(
            listOf(AppStateKeys.SNAPSHOT_EVENT_NAME, AppStateKeys.CHANGE_EVENT_NAME),
            logger.logs.map { it.eventName },
        )
        assertEquals(2L, logger.logs[1].attributes[AppStateKeys.SEQ])

        recorder.update(NETWORK, "5g", false, CHANGE_MS + 3)
        executor.runCurrentlyBlocked()
        assertEquals(5L, logger.logs[2].attributes[AppStateKeys.SEQ])
        assertEquals(2, telemetryService.appliedLimits.size)
    }

    @Test
    fun `a burst before the worker runs is drained in one task and in order`() {
        attach(StateExportMode.ASSOCIATION)
        recorder.declare(StateDefinition(NETWORK), "wifi", false, START_MS)
        recorder.openPart(PART, START_MS)
        recorder.update(NETWORK, "cell", false, CHANGE_MS)
        recorder.update(NETWORK, "none", false, CHANGE_MS + 1)
        recorder.update(NETWORK, "wifi", false, CHANGE_MS + 2)

        assertEquals(1, executor.submitCount)
        executor.runCurrentlyBlocked()

        assertEquals(
            listOf(
                AppStateKeys.SNAPSHOT_EVENT_NAME to START_MS.millisToNanos(),
                AppStateKeys.CHANGE_EVENT_NAME to CHANGE_MS.millisToNanos(),
                AppStateKeys.CHANGE_EVENT_NAME to (CHANGE_MS + 1).millisToNanos(),
                AppStateKeys.CHANGE_EVENT_NAME to (CHANGE_MS + 2).millisToNanos(),
            ),
            logger.logs.map { it.eventName to it.timestamp },
        )
        assertEquals(
            listOf("cell", "none", "wifi"),
            logger.logs.drop(1).map { it.attributes[AppStateKeys.NEW_VALUE] },
        )
    }

    @Test
    fun `an exception in the logger does not escape and the next event is still emitted`() {
        var calls = 0
        val failing = FakeOpenTelemetryLogger()
        attach(StateExportMode.ASSOCIATION, loggerProvider = {
            calls++
            if (calls == 1) {
                error("boom")
            }
            failing
        })
        recorder.declare(StateDefinition(NETWORK), "wifi", false, START_MS)
        recorder.openPart(PART, START_MS) // fails
        recorder.update(NETWORK, "cell", false, CHANGE_MS)
        executor.runCurrentlyBlocked()

        assertEquals(2, calls)
        assertEquals(listOf(AppStateKeys.CHANGE_EVENT_NAME), failing.logs.map(FakeLogRecord::eventName))
        assertEquals("boom", errorHandler.sdkErrorMessages.single().throwable?.message)
    }

    @Test
    fun `every record, change or snapshot, is emitted with the context that marks a replay`() {
        attach(StateExportMode.ASSOCIATION)
        recorder.declare(StateDefinition(NETWORK), "wifi", false, START_MS)
        recorder.openPart(PART, START_MS)
        recorder.update(NETWORK, "cell", false, CHANGE_MS)
        executor.runCurrentlyBlocked()

        assertEquals(
            listOf(AppStateKeys.SNAPSHOT_EVENT_NAME, AppStateKeys.CHANGE_EVENT_NAME),
            logger.logs.map { it.eventName },
        )
        logger.logs.forEach { assertTrue(checkNotNull(it.context).isStateReplay()) }
    }

    @Test
    fun `a change left out at the cap does not stop a part's snapshot being queued, in order after the changes before it`() {
        attach(StateExportMode.ASSOCIATION, maxQueued = 2)
        recorder.declare(StateDefinition(NETWORK), "wifi", false, START_MS)
        recorder.openPart(PART, START_MS) // queued: snapshot
        recorder.update(NETWORK, "cell", false, CHANGE_MS) // queued: change 2
        recorder.update(NETWORK, "none", false, CHANGE_MS + 1) // at the cap: left out
        recorder.openPart("part-2", PART_MS) // queued although the cap is reached
        executor.runCurrentlyBlocked()

        assertEquals(listOf(LIMIT_TYPE to AppliedLimitType.DROP), telemetryService.appliedLimits)
        assertEquals(
            listOf(AppStateKeys.SNAPSHOT_EVENT_NAME, AppStateKeys.CHANGE_EVENT_NAME, AppStateKeys.SNAPSHOT_EVENT_NAME),
            logger.logs.map { it.eventName },
        )
        // the number skips over the change that was left out, and the snapshot has the value that change made
        assertEquals(listOf<Any?>(1L, 2L, 3L), logger.logs.map { it.attributes[AppStateKeys.SEQ] })
        assertEquals("none", logger.logs[2].attributes["${AppStateKeys.VALUE_PREFIX}$NETWORK"])
    }

    @Test
    fun `nothing is emitted on the thread that made the change`() {
        val emittedOn = mutableListOf<String>()
        val worker = Executors.newSingleThreadScheduledExecutor { Thread(it, "replay-test-worker") }
        try {
            val emitter = StateReplayEmitter(
                loggerProvider = {
                    emittedOn.add(Thread.currentThread().name)
                    logger
                },
                worker = BackgroundWorker(worker),
                processIdentifier = { PROCESS_ID },
                stateTelemetry = StateTelemetry(recorder, exportMode = { StateExportMode.ASSOCIATION }),
                errorHandler = errorHandler,
                telemetryService = telemetryService,
                rootContextProvider = { fakeOpenTelemetry(OtelSdkMode.REGULAR).context.root() },
            )
            recorder.attach(emitter)
            recorder.declare(StateDefinition(NETWORK), "wifi", false, START_MS)
            recorder.openPart(PART, START_MS)
            recorder.update(NETWORK, "cell", false, CHANGE_MS)
        } finally {
            worker.shutdown()
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS))
        }

        assertEquals(2, logger.logs.size)
        assertEquals(listOf("replay-test-worker", "replay-test-worker"), emittedOn)
        assertNotEquals(Thread.currentThread().name, emittedOn.first())
    }

    private fun attach(
        mode: StateExportMode,
        maxQueued: Int = 1000,
        loggerProvider: () -> Logger = { logger },
    ): StateReplayEmitter {
        val emitter = StateReplayEmitter(
            loggerProvider = loggerProvider,
            worker = BackgroundWorker(executor),
            processIdentifier = { PROCESS_ID },
            stateTelemetry = StateTelemetry(recorder, exportMode = { mode }),
            errorHandler = errorHandler,
            telemetryService = telemetryService,
            maxQueued = maxQueued,
            rootContextProvider = { fakeOpenTelemetry(OtelSdkMode.REGULAR).context.root() },
        )
        recorder.attach(emitter)
        return emitter
    }

    private companion object {
        const val START_MS = 10_000L
        const val PART_MS = 10_500L
        const val CHANGE_MS = 11_000L
        const val PART = "part-1"
        const val PROCESS_ID = "process-id"
        const val NETWORK = "network"
        const val SCREEN = "screen-automatic"
        const val LIMIT_TYPE = "state_replay_event"
    }
}
