package io.embrace.android.embracesdk.internal.capture.state

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeLogRecord
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryLogger
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.fakeOpenTelemetry
import io.embrace.android.embracesdk.internal.arch.datasource.StateDeclaration
import io.embrace.android.embracesdk.internal.arch.datasource.StateUpdate
import io.embrace.android.embracesdk.internal.arch.schema.SystemStateValue
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.injection.WorkerThreadPoolImpl
import io.embrace.android.embracesdk.internal.otel.state.StateEventLogger
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.Worker
import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.logging.Logger
import io.opentelemetry.kotlin.logging.SeverityNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class StateEventEmitterTest {

    private lateinit var clock: FakeClock
    private lateinit var logger: FakeOpenTelemetryLogger
    private lateinit var executor: BlockingScheduledExecutorService
    private lateinit var recorder: StateRecorder
    private lateinit var emitter: StateEventEmitter

    @Before
    fun setUp() {
        clock = FakeClock()
        logger = FakeOpenTelemetryLogger()
        executor = BlockingScheduledExecutorService(clock, blockingMode = true)
    }

    @Test
    fun `each change is emitted off the thread that reported it, in order, with the value of every state after it`() {
        createRecorder(includeValues = true)
        beginPart()
        val wifiTime = update("network", "wifi")
        // one attribute under a key the state declares, one under a stem that is reserved, which no declaration gets, and
        // one that is not declared: only the first is carried
        val reported = mapOf("emb.state.dropped_by_instrumentation" to "2", "key" to "v", "undeclared" to "x")
        recorder.declare(StateDeclaration("screen-automatic", attributeKeys = reported.keys - "undeclared"))
        val screenTime = update("screen-automatic", Backgrounded, reported)
        update("network", "wifi")
        val offlineTime = update("network", "none")

        // reporting a change only queues it
        assertTrue(logger.logs.isEmpty())
        executor.runCurrentlyBlocked()

        // the part's start, which found no state with a value, and then the changes
        assertEquals("emb.app.state.snapshot", logger.logs.first().eventName)
        val changes = logger.logs.drop(1)
        assertEquals(listOf(1L, 2L, 3L), changes.map { it.attributes["emb.app.state.seq"] })
        with(changes[0]) {
            assertChangeEvent(wifiTime)
            assertEquals(
                changeAttributes(1, "network", "wifi") + mapOf("emb.app.state.value.network" to "wifi"),
                attributes,
            )
        }
        with(changes[1]) {
            assertChangeEvent(screenTime)
            assertEquals(
                changeAttributes(2, "screen-automatic", "Backgrounded") + mapOf(
                    "emb.app.state.new_value_type" to "system",
                    "key" to "v",
                    "emb.app.state.value.network" to "wifi",
                    "emb.app.state.value.screen-automatic" to "Backgrounded",
                    "emb.app.state.value_type.screen-automatic" to "system",
                ),
                attributes,
            )
        }
        with(changes[2]) {
            assertChangeEvent(offlineTime)
            assertEquals("none", attributes["emb.app.state.value.network"])
            // the value the state had before, and none on the first change of each state
            assertEquals("wifi", attributes["emb.app.state.previous_value"])
            assertNull(attributes["emb.app.state.previous_value_type"])
        }
        assertNull(changes[0].attributes["emb.app.state.previous_value"])
        assertNull(changes[1].attributes["emb.app.state.previous_value"])
        // the values on the first event are the ones after that change, and not the ones when it was emitted
        assertNull(changes[0].attributes["emb.app.state.value.screen-automatic"])
    }

    @Test
    fun `a change made with no session part in progress is not emitted, and the start of the next part carries the value it left`() {
        createRecorder(includeValues = true)
        // before the first part, and between two parts
        update("network", "wifi")
        update("network", "wan")
        beginPart()
        update("network", "none")
        recorder.endJournal(clock.tick())
        update("network", "wifi")
        update("power", "low")
        val secondPartStart = beginPart()
        executor.runCurrentlyBlocked()

        assertEquals(
            listOf("emb.app.state.snapshot", "emb.app.state.change", "emb.app.state.snapshot"),
            logger.logs.map(FakeLogRecord::eventName),
        )
        // the numbers the events carry skip the changes that were not emitted
        assertEquals(listOf(2L, 3L, 5L), logger.logs.map { it.attributes["emb.app.state.seq"] })
        assertEquals("wan", logger.logs[0].attributes["emb.app.state.value.network"])
        with(logger.logs[1]) {
            assertEquals("none", attributes["emb.app.state.new_value"])
            assertEquals("wan", attributes["emb.app.state.previous_value"])
        }
        with(logger.logs[2]) {
            assertEquals(secondPartStart.millisToNanos(), timestamp)
            assertEquals("wifi", attributes["emb.app.state.value.network"])
            assertEquals("low", attributes["emb.app.state.value.power"])
        }
    }

    @Test
    fun `without the values, a change is emitted alone, and the start of a session part carries every value either way`() {
        createRecorder(includeValues = false)
        beginPart()
        update("network", "wifi")
        update("screen-automatic", Backgrounded)
        recorder.endJournal(clock.tick())
        val partStart = beginPart()
        update("network", "none")
        executor.runCurrentlyBlocked()

        assertEquals(
            listOf(
                "emb.app.state.snapshot",
                "emb.app.state.change",
                "emb.app.state.change",
                "emb.app.state.snapshot",
                "emb.app.state.change",
            ),
            logger.logs.map(FakeLogRecord::eventName),
        )
        logger.logs.removeAt(0)
        assertEquals(changeAttributes(1, "network", "wifi"), logger.logs[0].attributes)
        assertEquals(
            changeAttributes(2, "screen-automatic", "Backgrounded") + mapOf("emb.app.state.new_value_type" to "system"),
            logger.logs[1].attributes,
        )
        with(logger.logs[2]) {
            assertNull(body)
            assertEquals(SeverityNumber.INFO, severityNumber)
            assertEquals(partStart.millisToNanos(), timestamp)
            assertEquals(
                mapOf(
                    "emb.type" to "state",
                    "emb.app.state.seq" to 2L,
                    "emb.entity.type" to "process",
                    "emb.entity.id" to PROCESS_IDENTIFIER,
                    "emb.app.state.value.network" to "wifi",
                    "emb.app.state.value.screen-automatic" to "Backgrounded",
                    "emb.app.state.value_type.screen-automatic" to "system",
                ),
                attributes,
            )
        }
        assertEquals(
            changeAttributes(3, "network", "none") + mapOf("emb.app.state.previous_value" to "wifi"),
            logger.logs[3].attributes,
        )
    }

    @Test
    fun `a change from a value the SDK generated says so, in both renderings, and the start of a session part carries no previous value`() {
        listOf(true, false).forEach { includeValues ->
            logger.logs.clear()
            createRecorder(includeValues = includeValues)
            beginPart()
            update("screen-automatic", Backgrounded)
            update("screen-automatic", "Home")
            recorder.endJournal(clock.tick())
            beginPart()
            executor.runCurrentlyBlocked()
            logger.logs.removeAt(0)

            val change = logger.logs[1]
            assertEquals("Home", change.attributes["emb.app.state.new_value"])
            assertEquals("Backgrounded", change.attributes["emb.app.state.previous_value"])
            assertEquals("system", change.attributes["emb.app.state.previous_value_type"])
            assertNull(change.attributes["emb.app.state.new_value_type"])
            assertNull(logger.logs[0].attributes["emb.app.state.previous_value"])
            val snapshot = logger.logs[2]
            assertEquals("emb.app.state.snapshot", snapshot.eventName)
            assertEquals(emptyList<String>(), snapshot.attributes.keys.filter { it.contains("previous") })
        }
    }

    @Test
    fun `no more than the limit wait to be emitted, and the ones left out are counted`() {
        createRecorder(includeValues = true, maxPendingEvents = 3)
        beginPart()
        executor.runCurrentlyBlocked()
        logger.logs.clear()
        repeat(5) { index -> update("network", "value-$index") }

        // nothing has been emitted yet, so the fourth and fifth changes found three waiting
        assertEquals(3, emitter.pendingEvents())
        assertEquals(2, emitter.droppedEvents())
        executor.runCurrentlyBlocked()

        // what receives the events sees the sequence numbers stop short, and room is made again
        assertEquals(listOf(1L, 2L, 3L), logger.logs.map { it.attributes["emb.app.state.seq"] })
        assertEquals(0, emitter.pendingEvents())
        update("network", "after")
        executor.runCurrentlyBlocked()
        assertEquals(6L, logger.logs.last().attributes["emb.app.state.seq"])
        assertEquals(2, emitter.droppedEvents())
    }

    @Test
    fun `a change a limit refuses is not emitted, whether the limit is the journal's or the state's own`() {
        createRecorder(includeValues = false, maxChanges = 2)
        recorder.beginJournal(clock.now()) { }
        update("network", "wifi", maxChangesInPart = 1)
        // past the state's own limit
        update("network", "none", maxChangesInPart = 1)
        update("power", "low")
        // past the journal's
        update("power", "normal")
        executor.runCurrentlyBlocked()

        assertEquals(
            listOf("emb.app.state.snapshot", "emb.app.state.change", "emb.app.state.change"),
            logger.logs.map(FakeLogRecord::eventName),
        )
        assertEquals(listOf("wifi", "low"), logger.logs.drop(1).map { it.attributes["emb.app.state.new_value"] })
        // what receives the events sees the numbers skip where a change was refused, as the change log's do
        assertEquals(listOf(1L, 3L), logger.logs.drop(1).map { it.attributes["emb.app.state.seq"] })
    }

    @Test
    fun `events reported while one is being emitted follow it in order, and a worker that shuts down emits what it had`() {
        // the SDK's own workers, which turn a task away by cancelling it and do not throw
        val workers = WorkerThreadPoolImpl(FakeErrorHandler())
        val emitting = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val waitingLogger = object : Logger by logger {
            override fun emit(
                body: Any?,
                eventName: String?,
                timestamp: Long?,
                observedTimestamp: Long?,
                context: Context?,
                severityNumber: SeverityNumber?,
                severityText: String?,
                exception: Throwable?,
                attributes: (AttributesMutator.() -> Unit)?,
            ) {
                emitting.countDown()
                resume.await(WAIT_SECONDS, TimeUnit.SECONDS)
                logger.emit(
                    body,
                    eventName,
                    timestamp,
                    observedTimestamp,
                    context,
                    severityNumber,
                    severityText,
                    exception,
                    attributes,
                )
            }
        }
        createRecorder(
            includeValues = false,
            worker = workers.backgroundWorker(Worker.Background.StateExportWorker),
            eventLogger = waitingLogger,
        )

        beginPart()
        assertTrue(emitting.await(WAIT_SECONDS, TimeUnit.SECONDS))
        // the worker is in the middle of emitting the first event, the part's start, while the changes are reported
        repeat(EVENTS) { update("network", "value-$it") }
        assertEquals(EVENTS, emitter.pendingEvents())

        // the workers shut down with the SDK while that pass is under way, and it still emits everything it was left
        workers.close()
        resume.countDown()
        assertTrue(workers.awaitTermination(TimeUnit.SECONDS.toMillis(WAIT_SECONDS)))
        assertEquals((0..EVENTS).map(Int::toLong), logger.logs.map { it.attributes["emb.app.state.seq"] })
        assertEquals(0, emitter.pendingEvents())

        // a change reported once the worker has gone is not emitted, and nothing is kept of it
        repeat(EVENTS) { update("network", "after-shutdown-$it") }
        assertEquals(EVENTS + 1, logger.logs.size)
        assertEquals(0, emitter.pendingEvents())
    }

    private fun FakeLogRecord.assertChangeEvent(timeMs: Long) {
        assertEquals("emb.app.state.change", eventName)
        assertNull(body)
        assertEquals(SeverityNumber.INFO, severityNumber)
        assertEquals("INFO", severityText)
        assertEquals(timeMs.millisToNanos(), timestamp)
        assertEquals(timeMs.millisToNanos(), observedTimestamp)
    }

    private fun changeAttributes(sequence: Long, state: String, value: String): Map<String, Any> = mapOf(
        "emb.type" to "state",
        "emb.app.state.seq" to sequence,
        "emb.entity.type" to "process",
        "emb.entity.id" to PROCESS_IDENTIFIER,
        "emb.app.state.name" to state,
        "emb.app.state.new_value" to value,
    )

    private fun createRecorder(
        includeValues: Boolean,
        maxChanges: Int = StateRecorder.MAX_JOURNAL_CHANGES,
        worker: BackgroundWorker = BackgroundWorker(executor),
        eventLogger: Logger = logger,
        maxPendingEvents: Int = 1_000,
    ) {
        emitter = StateEventEmitter(
            logger = StateEventLogger({ eventLogger }, { fakeOpenTelemetry(OtelSdkMode.REGULAR).context.root() }),
            worker = worker,
            processIdentifierProvider = { PROCESS_IDENTIFIER },
            includeValues = includeValues,
            maxPendingEvents = maxPendingEvents,
        )
        recorder = StateRecorder(clock, maxChanges = maxChanges, listener = emitter)
    }

    /**
     * Starts a session part's change log, which is what a change has to be made during to be emitted.
     */
    private fun beginPart(): Long {
        val time = clock.tick(1000)
        recorder.beginJournal(time) { }
        return time
    }

    /**
     * Reports [value] for [state], as its data source would: the state is declared first, with [maxChangesInPart] and no
     * attribute keys, unless a test has declared it already.
     */
    private fun update(
        state: String,
        value: Any,
        attributes: Map<String, String> = emptyMap(),
        maxChangesInPart: Int = StateDeclaration.DEFAULT_MAX_CHANGES_IN_PART,
    ): Long {
        recorder.declare(StateDeclaration(state, maxChangesInPart))
        val time = clock.tick()
        recorder.onEvent(StateUpdate(state, value, time, attributes))
        return time
    }

    private object Backgrounded : SystemStateValue {
        override fun toString(): String = "Backgrounded"
    }

    private companion object {
        const val PROCESS_IDENTIFIER = "process-identifier"
        const val EVENTS = 50
        const val WAIT_SECONDS = 10L
    }
}
