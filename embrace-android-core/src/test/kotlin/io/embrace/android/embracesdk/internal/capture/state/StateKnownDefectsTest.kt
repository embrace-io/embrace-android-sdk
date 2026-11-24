package io.embrace.android.embracesdk.internal.capture.state

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryLogger
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.fakeOpenTelemetry
import io.embrace.android.embracesdk.internal.arch.datasource.StateUpdate
import io.embrace.android.embracesdk.internal.clock.millisToNanos
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
    fun `a change's own attribute is kept beside the SDK's under an equal key, and stands under a key the SDK does not write`() {
        val recorder = StateRecorder(clock, listener = emitter())
        recorder.beginJournal(clock.now(), journal::addAll)
        recorder.onEvent(
            StateUpdate(
                state = "network",
                value = "none",
                timeMs = clock.now(),
                attributes = mapOf(
                    "emb.state.new_value" to "wifi",
                    "emb.state.value_type" to "system",
                    "emb.state.new_value_type" to "system",
                    "emb.state.value.network" to "wifi",
                ),
            ),
        )
        recorder.endJournal(clock.now())

        // in the change log both of two equal keys are kept, the reporter's first, and the reporter's mark of a generated
        // value stands on a value that was observed
        val delivered = checkNotNull(checkNotNull(stateSpan().events).single().attributes)
        assertEquals(listOf("wifi", "none"), delivered.filter { it.key == "emb.state.new_value" }.map { it.data })
        assertEquals(listOf("system"), delivered.filter { it.key == "emb.state.value_type" }.map { it.data })

        // in the event for an app's exporters the SDK's wins under a key it writes, and the reporter's stands under one it
        // does not write: the mark of a generated value, and a state's value where the event carries no values
        executor.runCurrentlyBlocked()
        val exported = logger.logs.single { it.eventName == "emb.state.change" }.attributes
        assertEquals("none", exported["emb.state.new_value"])
        assertEquals("system", exported["emb.state.new_value_type"])
        assertEquals("wifi", exported["emb.state.value.network"])
    }

    @Test
    fun `the event left out when too many are waiting can be the one that says a session part started`() {
        val emitter = emitter(maxPendingEvents = 1)
        val recorder = StateRecorder(clock, listener = emitter)
        recorder.onEvent(StateUpdate("network", "wifi", clock.now()))
        recorder.beginJournal(clock.now(), journal::addAll)

        assertEquals(1, emitter.droppedEvents())
        executor.runCurrentlyBlocked()
        assertEquals(listOf("emb.state.change"), logger.logs.map { it.eventName })
    }

    @Test
    fun `a change is listed by when it was recorded and not by its time, so an event can lie outside its span with no mark`() {
        val recorder = StateRecorder(clock)
        recorder.onEvent(StateUpdate("network", "wifi", PART_START_MS + 500))
        recorder.beginJournal(PART_START_MS, journal::addAll)
        recorder.onEvent(StateUpdate("network", "wan", PART_START_MS - 500))
        recorder.onEvent(StateUpdate("network", "none", PART_END_MS + 500))
        recorder.endJournal(PART_END_MS + 1_000)

        // a change timed after the part started is listed before the part start, and one timed before it is listed after
        val partStart = journal.indexOfFirst { it.part_start != null }
        assertEquals(PART_START_MS, journal[partStart].time_ms)
        assertEquals("wifi" to PART_START_MS + 500, journal[partStart - 1].let { it.change?.value_?.value_ to it.time_ms })
        assertEquals("wan" to PART_START_MS - 500, journal[partStart + 1].let { it.change?.value_?.value_ to it.time_ms })

        // the span covers the session part, two of its three events lie outside it, and nothing says so
        val stateSpan = stateSpan()
        assertEquals(PART_START_MS.millisToNanos(), stateSpan.startTimeNanos)
        assertEquals(PART_END_MS.millisToNanos(), stateSpan.endTimeNanos)
        assertEquals(
            listOf(PART_START_MS + 500, PART_START_MS - 500, PART_END_MS + 500).map { it.millisToNanos() },
            checkNotNull(stateSpan.events).map { it.timestampNanos },
        )
        val attributes = checkNotNull(stateSpan.attributes).associate { it.key to it.data }
        assertEquals("1", attributes["emb.state.part_start_seq"])
        assertNull(attributes["emb.state.journal_truncated"])
    }

    @Test
    fun `a change folded and a change dropped are reported through the one callback, so one limit name counts both`() {
        var reported = 0
        val recorder = StateRecorder(clock, maxChanges = 2, maxHeldChanges = 1, onChangeDropped = { reported++ })
        recorder.onEvent(StateUpdate("network", "wifi", clock.now()))
        recorder.onEvent(StateUpdate("network", "wan", clock.now()))
        assertEquals(1, reported)

        recorder.beginJournal(clock.now(), journal::addAll)
        recorder.onEvent(StateUpdate("network", "none", clock.now()))
        recorder.onEvent(StateUpdate("network", "wifi", clock.now()))
        recorder.endJournal(clock.now())
        assertEquals(2, reported)

        // the journal keeps apart what the callback does not: one change was folded and one was dropped
        assertEquals(1, journal.single { it.part_start != null }.part_start?.dropped_changes)
        assertEquals(1, journal.single { it.part_end != null }.part_end?.dropped_changes)
    }

    @Test
    fun `a state's name is not checked, so an empty one and a long one with a line break reach attribute keys and event names`() {
        val unchecked = "line\nbreak " + "x".repeat(5_000)
        val recorder = StateRecorder(clock)
        recorder.beginJournal(clock.now(), journal::addAll)
        recorder.onEvent(StateUpdate("", "empty", clock.now()))
        recorder.onEvent(StateUpdate(unchecked, "long", clock.now()))
        recorder.endJournal(clock.now())

        val stamp = recorder.snapshot().attributes
        assertEquals("empty", stamp["emb.state.value."])
        assertEquals("long", stamp["emb.state.value.$unchecked"])
        val eventNames = checkNotNull(stateSpan().events).map { it.name }
        assertEquals(listOf("emb-state-", "emb-state-$unchecked"), eventNames)
        assertTrue(checkNotNull(eventNames.last()).length > 5_000)
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
