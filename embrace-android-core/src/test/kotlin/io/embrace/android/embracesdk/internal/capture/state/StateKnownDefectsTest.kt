package io.embrace.android.embracesdk.internal.capture.state

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryLogger
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.TestStateDataSource
import io.embrace.android.embracesdk.fakes.fakeOpenTelemetry
import io.embrace.android.embracesdk.internal.arch.datasource.StateDeclaration
import io.embrace.android.embracesdk.internal.arch.datasource.StateUpdate
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.config.resolved.AutoDataCaptureConfig
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.config.resolved.PersistenceConfig
import io.embrace.android.embracesdk.internal.otel.state.StateEventLogger
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.persistence.StateRecord
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Records defects the Android design of state capture lists under "Known to be wrong, or weak". Each test asserts what the
 * code does today, so it passes because its defect is present, and it is to be turned around when that defect is fixed.
 */
internal class StateKnownDefectsTest {

    private lateinit var clock: FakeClock
    private lateinit var journal: MutableList<StateRecord>
    private lateinit var logger: FakeOpenTelemetryLogger
    private lateinit var executor: BlockingScheduledExecutorService

    private val sessionSpan = Span(
        traceId = "a".repeat(32),
        spanId = "b".repeat(16),
        name = "emb-session",
        startTimeNanos = PART_START_MS.millisToNanos(),
        endTimeNanos = PART_END_MS.millisToNanos(),
        attributes = listOf(
            Attribute("emb.type", "ux.session"),
            Attribute("emb.session_part_id", "part"),
        ),
    )

    @Before
    fun setUp() {
        clock = FakeClock()
        journal = mutableListOf()
        logger = FakeOpenTelemetryLogger()
        executor = BlockingScheduledExecutorService(clock, blockingMode = true)
    }

    @Test
    fun `the event left out when too many are waiting can be the one that says a session part started`() {
        val emitter = emitter(maxPendingEvents = 1)
        val recorder = StateRecorder(clock, listener = emitter).declaring("network")
        recorder.beginJournal(clock.now(), journal::addAll)
        executor.runCurrentlyBlocked()
        // a change of the first part is still waiting to be emitted when the second part starts
        recorder.onEvent(StateUpdate("network", "wifi", clock.now()))
        recorder.endJournal(clock.now())
        recorder.beginJournal(clock.now(), mutableListOf<StateRecord>()::addAll)

        assertEquals(1, emitter.droppedEvents())
        executor.runCurrentlyBlocked()
        // the first part's start and its change are emitted, and nothing says the second part started
        assertEquals(listOf("emb.app.state.snapshot", "emb.app.state.change"), logger.logs.map { it.eventName })
    }

    @Test
    fun `a change is listed or counted by when it was recorded and not by its time, so an event can lie outside its span with no mark`() {
        val recorder = StateRecorder(clock).declaring("network")
        recorder.onEvent(StateUpdate("network", "wifi", PART_START_MS + 500))
        recorder.beginJournal(PART_START_MS, journal::addAll)
        recorder.onEvent(StateUpdate("network", "wan", PART_START_MS - 500))
        recorder.onEvent(StateUpdate("network", "none", PART_END_MS + 500))
        recorder.endJournal(PART_END_MS + 1_000)

        // a change timed after the part started is counted as made with no session part, and listed nowhere, and one
        // timed before the part started is listed in it
        val partStart = journal.single { it.part_start != null }
        assertEquals(PART_START_MS, partStart.time_ms)
        assertEquals(listOf("network" to 1), partStart.part_start?.not_in_session_counts?.map { it.state to it.count })
        assertEquals(
            listOf("wan" to PART_START_MS - 500, "none" to PART_END_MS + 500),
            journal.filter { it.change != null }.map { it.change?.value_?.value_ to it.time_ms },
        )

        // the span covers the session part, both of its events lie outside it, and nothing says so
        val stateSpan = stateSpan()
        assertEquals(PART_START_MS.millisToNanos(), stateSpan.startTimeNanos)
        assertEquals(PART_END_MS.millisToNanos(), stateSpan.endTimeNanos)
        assertEquals(
            listOf(PART_START_MS - 500, PART_END_MS + 500).map { it.millisToNanos() },
            checkNotNull(stateSpan.events).map { it.timestampNanos },
        )
        val attributes = checkNotNull(stateSpan.attributes).associate { it.key to it.data }
        assertEquals("1", attributes["emb.app.state.not_in_session_count.network"])
        assertNull(attributes["emb.app.state.truncated"])
    }

    @Test
    fun `a state's name is not checked, so an empty one and a long one with a line break reach attribute keys and event names`() {
        val unchecked = "line\nbreak " + "x".repeat(5_000)
        // a name is taken as its data source declares it
        val recorder = StateRecorder(clock).declaring("", unchecked)
        recorder.beginJournal(clock.now(), journal::addAll)
        recorder.onEvent(StateUpdate("", "empty", clock.now()))
        recorder.onEvent(StateUpdate(unchecked, "long", clock.now()))
        recorder.endJournal(clock.now())

        val stamp = recorder.snapshot().attributes
        assertEquals("empty", stamp["emb.app.state.value."])
        assertEquals("long", stamp["emb.app.state.value.$unchecked"])
        val eventNames = checkNotNull(stateSpan().events).map { it.name }
        assertEquals(listOf("emb-state-", "emb-state-$unchecked"), eventNames)
        assertTrue(checkNotNull(eventNames.last()).length > 5_000)
    }

    @Test
    fun `the data source and the recorder each decide what a change is, so one can report a change the other takes for a repeat`() {
        val args = FakeInstrumentationArgs(
            configService = FakeConfigService(
                config = EmbraceConfig(
                    persistence = { PersistenceConfig(multiFileEnabled = { true }) },
                    autoDataCapture = { AutoDataCaptureConfig(sharedStateSpanEnabled = { true }) },
                ),
            ),
        )
        val recorder = StateRecorder(clock)
        val emitted = mutableListOf<StateUpdate>()
        args.eventBus.addHandler(StateUpdate.KEY) { emitted += it }
        recorder.listenTo(args.eventBus)
        val dataSource = TestStateDataSource(args)
        recorder.beginJournal(clock.now(), journal::addAll)
        dataSource.onDataCaptureEnabled()

        // two values that are the same up to where a value is cut
        val sameUpToTheCut = "v".repeat(2_000)
        dataSource.onStateChange(sameUpToTheCut + "a", clock.now())
        dataSource.onStateChange(sameUpToTheCut + "b", clock.now())
        recorder.endJournal(clock.now())

        // the data source compares the values, and emits each as a change
        assertEquals(3, emitted.size)
        // the recorder compares the cut text: the second takes no number, and is counted nowhere as it is no loss to it
        assertEquals(2L, recorder.snapshot().sequence)
        assertEquals(2, journal.count { it.change != null })
        assertEquals(emptyList<Any>(), checkNotNull(journal.last().part_end).dropped_counts)
    }

    /**
     * This recorder with each of [states] declared, as a data source declares its state when its capture starts.
     */
    private fun StateRecorder.declaring(vararg states: String): StateRecorder = apply {
        states.forEach { declare(StateDeclaration(it)) }
    }

    /**
     * An emitter of the association rendering, in which an event for a change carries no state's value.
     */
    private fun emitter(maxPendingEvents: Int = 1_000): StateEventEmitter = StateEventEmitter(
        logger = StateEventLogger({ logger }, { fakeOpenTelemetry(OtelSdkMode.REGULAR).context.root() }),
        worker = BackgroundWorker(executor),
        processIdentifierProvider = { "process-identifier" },
        includeValues = false,
        maxPendingEvents = maxPendingEvents,
    )

    /**
     * The span the journal is delivered as, with a session part that ran from [PART_START_MS] to [PART_END_MS].
     */
    private fun stateSpan(): Span {
        val envelope = Envelope(data = SessionPartPayload(spans = listOf(sessionSpan)))
        return checkNotNull(envelope.withStateSpan(journal).data.spans).last()
    }

    private companion object {
        const val PART_START_MS = 1_000L
        const val PART_END_MS = 2_000L
    }
}
