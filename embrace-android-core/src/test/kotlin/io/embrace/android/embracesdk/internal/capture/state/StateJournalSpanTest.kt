package io.embrace.android.embracesdk.internal.capture.state

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.arch.datasource.StateUpdate
import io.embrace.android.embracesdk.internal.arch.schema.SystemStateValue
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.persistence.StateRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class StateJournalSpanTest {

    private lateinit var clock: FakeClock
    private lateinit var recorder: StateRecorder
    private lateinit var journal: MutableList<StateRecord>

    private val sessionSpan = Span(
        traceId = "a".repeat(32),
        spanId = "b".repeat(16),
        name = "emb-session",
        attributes = listOf(
            Attribute("emb.type", "ux.session"),
            Attribute("emb.process_identifier", "process"),
            Attribute("emb.session_part_id", "part"),
            Attribute("emb.user_session_id", "user-session"),
        ),
    )
    private val otherSpan = Span(spanId = "c".repeat(16), name = "other")

    @Before
    fun setUp() {
        clock = FakeClock()
        recorder = StateRecorder(clock)
        journal = mutableListOf()
    }

    @Test
    fun `a journal of a part that ended is delivered as a completed span`() {
        update("network", "wifi")
        update("screen", Backgrounded)
        recorder.endJournal(clock.now())
        val heldChangeTime = update("network", "wan")
        val partStart = clock.tick(1000)
        recorder.beginJournal(partStart, journal::addAll)
        val changeTime = update("screen", "Home", mapOf("key" to "value"))
        val partEnd = clock.tick(1000)
        recorder.endJournal(partEnd)

        val envelope = envelope(spans = listOf(otherSpan, sessionSpan)).withStateSpan(journal)

        assertNull(envelope.data.spanSnapshots)
        val spans = checkNotNull(envelope.data.spans)
        assertEquals(listOf("other", "emb-session", "emb-states"), spans.map(Span::name))
        assertSame(otherSpan, spans[0])

        val stateSpan = spans[2]
        assertEquals(partStart.millisToNanos(), stateSpan.startTimeNanos)
        assertEquals(partEnd.millisToNanos(), stateSpan.endTimeNanos)
        assertEquals(32, stateSpan.traceId?.length)
        assertEquals(16, stateSpan.spanId?.length)
        assertEquals(
            mapOf(
                "emb.type" to "state",
                "emb.process_identifier" to "process",
                "emb.session_part_id" to "part",
                "emb.user_session_id" to "user-session",
                // nothing had a value when the process started, which is what this first journal opens with
                "emb.states.initial_seq" to "0",
                "emb.states.part_start_seq" to "3",
            ),
            stateSpan.attributeMap(),
        )

        val events = checkNotNull(stateSpan.events)
        assertEquals(
            listOf("emb-state-network", "emb-state-screen", "emb-state-network", "emb-state-screen"),
            events.map { it.name },
        )
        // an event carries its own number, which is its position after the journal's opening values
        assertEquals(listOf("1", "2", "3", "4"), events.map { it.attributeMap()["emb.states.seq"] })
        assertEquals(
            mapOf("emb.states.seq" to "2", "emb.states.new_value" to "Backgrounded", "emb.states.new_value_type" to "system"),
            events[1].attributeMap(),
        )
        // made before the part started, so it is earlier than the span itself
        assertEquals(heldChangeTime.millisToNanos(), events[2].timestampNanos)
        assertTrue(checkNotNull(events[2].timestampNanos) < checkNotNull(stateSpan.startTimeNanos))
        assertEquals(changeTime.millisToNanos(), events[3].timestampNanos)
        assertEquals(mapOf("key" to "value", "emb.states.seq" to "4", "emb.states.new_value" to "Home"), events[3].attributeMap())

        val link = checkNotNull(spans[1].links).single()
        assertEquals(stateSpan.spanId, link.spanId)
        assertEquals(stateSpan.traceId, link.traceId)
        assertEquals(
            mapOf("emb.link_type" to "STATE", "emb.session_part_id" to "part"),
            checkNotNull(link.attributes).associate { it.key to it.data },
        )
    }

    @Test
    fun `a journal that opens with values and never ended is delivered as a snapshot with those values`() {
        recorder.beginJournal(clock.now(), mutableListOf<StateRecord>()::addAll)
        update("network", "wifi")
        update("screen", Backgrounded)
        recorder.endJournal(clock.now())
        val partStart = clock.tick(1000)
        recorder.beginJournal(partStart, journal::addAll)
        update("network", "wan")

        // the process died here, with the session part span still a snapshot
        val envelope = envelope(snapshots = listOf(sessionSpan)).withStateSpan(journal)

        assertEquals(emptyList<Span>(), envelope.data.spans.orEmpty())
        val snapshots = checkNotNull(envelope.data.spanSnapshots)
        assertEquals(listOf("emb-session", "emb-states"), snapshots.map(Span::name))
        val stateSpan = snapshots[1]
        assertNull(stateSpan.endTimeNanos)
        assertEquals(partStart.millisToNanos(), stateSpan.startTimeNanos)
        val attributes = stateSpan.attributeMap()
        assertEquals("wifi", attributes["emb.states.initial_value.network"])
        assertNull(attributes["emb.states.initial_value_type.network"])
        assertEquals("Backgrounded", attributes["emb.states.initial_value.screen"])
        assertEquals("system", attributes["emb.states.initial_value_type.screen"])
        assertEquals("2", attributes["emb.states.initial_seq"])
        assertEquals("2", attributes["emb.states.part_start_seq"])
        // nothing says how the part ended, so nothing says the events lead to the values it ended with
        assertEquals("true", attributes["emb.states.truncated"])
        assertEquals(listOf("wan"), checkNotNull(stateSpan.events).map { it.attributeMap()["emb.states.new_value"] })
        assertEquals(listOf("3"), checkNotNull(stateSpan.events).map { it.attributeMap()["emb.states.seq"] })
        assertEquals(1, snapshots[0].links?.size)
    }

    @Test
    fun `a span a dead process left running that started before the last change a journal lists is given that change as its end`() {
        recorder.beginJournal(clock.now(), journal::addAll)
        repeat(9) { update("network", "value-$it") }
        // the process died here: no part end, and the session part span and the others still snapshots
        val running = Span(
            traceId = "c".repeat(32),
            spanId = "d".repeat(16),
            name = "running",
            attributes = listOf(Attribute("emb.states.seq", "5")),
        )
        val beforeFirstChange = running.copy(name = "early", attributes = emptyList())
        val completed = otherSpan

        val envelope = envelope(spans = listOf(completed), snapshots = listOf(sessionSpan, running, beforeFirstChange))
            .withStateSpan(journal, completesRunningSpans = true)
        val snapshots = checkNotNull(envelope.data.spanSnapshots).associateBy { it.name }

        assertEquals("9", snapshots.getValue("running").attributeMap()["emb.states.end_seq"])
        // started before the first change, so every change the journal lists happened while it ran
        assertEquals("9", snapshots.getValue("early").attributeMap()["emb.states.end_seq"])
        // a span that was complete is not touched
        assertEquals(completed, checkNotNull(envelope.data.spans).first { it.name == "other" })
    }

    @Test
    fun `a span carried over a part boundary is given the number the journal's checkpoint and part start hold`() {
        // nine changes are folded into the checkpoint, and the process is killed before another is made
        recorder = StateRecorder(clock, maxChanges = 100, maxHeldChanges = 0)
        repeat(9) { update("network", "value-$it") }
        recorder.beginJournal(clock.now(), journal::addAll)
        assertEquals(emptyList<StateRecord>(), journal.filter { it.change != null })
        val carried = Span(
            traceId = "c".repeat(32),
            spanId = "d".repeat(16),
            name = "carried",
            attributes = listOf(Attribute("emb.states.seq", "5")),
        )

        val envelope = envelope(snapshots = listOf(sessionSpan, carried)).withStateSpan(journal, completesRunningSpans = true)

        assertEquals(
            "9",
            checkNotNull(envelope.data.spanSnapshots).first { it.name == "carried" }.attributeMap()["emb.states.end_seq"],
        )
    }

    @Test
    fun `a span with no number is given an end number only if another span of the part has one`() {
        recorder.beginJournal(clock.now(), journal::addAll)
        repeat(3) { update("network", "value-$it") }
        val unnumbered =
            Span(traceId = "c".repeat(32), spanId = "d".repeat(16), name = "unnumbered", attributes = emptyList())
        val numbered = unnumbered.copy(name = "numbered", attributes = listOf(Attribute("emb.states.seq", "1")))

        // no span has a number, as when association was off in the process that died
        val noneLinked = envelope(snapshots = listOf(sessionSpan, unnumbered)).withStateSpan(journal, completesRunningSpans = true)
        assertNull(
            checkNotNull(noneLinked.data.spanSnapshots).first {
                it.name == "unnumbered"
            }.attributeMap()["emb.states.end_seq"],
        )

        // another span has one, so the process was linking spans and this one started before the first change
        val someLinked = envelope(snapshots = listOf(sessionSpan, unnumbered, numbered))
            .withStateSpan(journal, completesRunningSpans = true)
        val delivered = checkNotNull(someLinked.data.spanSnapshots).associateBy { it.name }
        assertEquals("3", delivered.getValue("unnumbered").attributeMap()["emb.states.end_seq"])
        assertEquals("3", delivered.getValue("numbered").attributeMap()["emb.states.end_seq"])
    }

    @Test
    fun `a running span is given no end number if no change came after its start, or the part has no journal, or it is not asked for`() {
        recorder.beginJournal(clock.now(), journal::addAll)
        repeat(5) { update("network", "value-$it") }
        val running = Span(
            traceId = "c".repeat(32),
            spanId = "d".repeat(16),
            name = "running",
            attributes = listOf(Attribute("emb.states.seq", "5")),
        )
        val dead = envelope(snapshots = listOf(sessionSpan, running))

        // the journal ends at the number the span started at
        val noLaterChange = dead.withStateSpan(journal, completesRunningSpans = true)
        assertNull(
            checkNotNull(noLaterChange.data.spanSnapshots).first {
                it.name == "running"
            }.attributeMap()["emb.states.end_seq"],
        )

        // not asked for: the number is not given even though a change came after
        repeat(4) { update("network", "later-$it") }
        val notAsked = dead.withStateSpan(journal)
        assertNull(
            checkNotNull(notAsked.data.spanSnapshots).first {
                it.name == "running"
            }.attributeMap()["emb.states.end_seq"],
        )

        // no journal: the envelope is as it was
        assertSame(dead, dead.withStateSpan(emptyList(), completesRunningSpans = true))

        // a part that ended normally is not a dead one, whatever spans were still open
        recorder.endJournal(clock.now())
        val ended = envelope(spans = listOf(sessionSpan), snapshots = listOf(running))
            .withStateSpan(journal, completesRunningSpans = true)
        assertNull(checkNotNull(ended.data.spanSnapshots).first { it.name == "running" }.attributeMap()["emb.states.end_seq"])
    }

    @Test
    fun `changes a journal dropped are counted and the values it ended with are delivered`() {
        recorder = StateRecorder(clock, maxChanges = 1)
        recorder.beginJournal(clock.now(), journal::addAll)
        update("network", "wifi")
        update("network", "wan")
        recorder.endJournal(clock.now())

        val stateSpan = checkNotNull(envelope(spans = listOf(sessionSpan)).withStateSpan(journal).data.spans).last()

        assertEquals(1, stateSpan.events?.size)
        assertEquals("1", stateSpan.attributeMap()["emb.states.dropped_changes"])
        assertEquals("wan", stateSpan.attributeMap()["emb.states.final_value.network"])
    }

    @Test
    fun `changes folded in before the part and changes dropped during it are counted apart`() {
        recorder = StateRecorder(clock, maxChanges = 2, maxHeldChanges = 1)
        update("network", "wifi")
        update("network", "wan")
        recorder.beginJournal(clock.now(), journal::addAll)
        update("screen", Backgrounded)
        update("network", "none")
        recorder.endJournal(clock.now())

        val stateSpan = stateSpanOf(listOf(sessionSpan))
        val attributes = stateSpan.attributeMap()

        // the first change is behind the values the span opens with, and the last is past the changes it holds
        assertEquals("1", attributes["emb.states.folded_changes"])
        assertEquals("1", attributes["emb.states.initial_seq"])
        assertEquals("wifi", attributes["emb.states.initial_value.network"])
        assertEquals(listOf("wan", "Backgrounded"), stateSpan.newValues())
        // the numbers follow initial_seq, and the dropped change has none
        assertEquals(listOf("2", "3"), checkNotNull(stateSpan.events).map { it.attributeMap()["emb.states.seq"] })
        assertEquals("1", attributes["emb.states.dropped_changes"])
        assertEquals("none", attributes["emb.states.final_value.network"])
        assertNull(attributes["emb.states.final_value_type.network"])
        assertEquals("Backgrounded", attributes["emb.states.final_value.screen"])
        assertEquals("system", attributes["emb.states.final_value_type.screen"])
        assertNull(attributes["emb.states.truncated"])
    }

    @Test
    fun `a journal that stops before its part start holds only changes made before the part`() {
        update("network", "wifi")
        update("network", "wan")
        recorder.beginJournal(clock.now(), journal::addAll)
        val stopped = journal.takeWhile { it.part_start == null }

        val attributes = checkNotNull(envelope(spans = listOf(sessionSpan)).withStateSpan(stopped).data.spanSnapshots)
            .last()
            .attributeMap()

        assertEquals("0", attributes["emb.states.initial_seq"])
        assertEquals("2", attributes["emb.states.part_start_seq"])
        assertEquals("true", attributes["emb.states.truncated"])
    }

    @Test
    fun `the span covers its session part and a part delivered twice delivers the same span`() {
        recorder.beginJournal(clock.now(), journal::addAll)
        update("network", "wifi")
        recorder.endJournal(clock.tick(1000))
        val timed = sessionSpan.copy(startTimeNanos = SESSION_START_NANOS, endTimeNanos = SESSION_END_NANOS)
        val otherPart = timed.copy(
            attributes = timed.attributes?.map { if (it.key == "emb.session_part_id") Attribute(it.key, "other") else it },
        )

        val first = stateSpanOf(listOf(timed))
        val second = stateSpanOf(listOf(timed))
        val other = stateSpanOf(listOf(otherPart))

        assertEquals(SESSION_START_NANOS, first.startTimeNanos)
        assertEquals(SESSION_END_NANOS, first.endTimeNanos)
        assertNull(first.attributeMap()["emb.states.truncated"])
        assertEquals(first.spanId, second.spanId)
        assertEquals(first.traceId, second.traceId)
        assertNotEquals(first.spanId, other.spanId)
        assertNotEquals(first.traceId, other.traceId)
    }

    @Test
    fun `a journal that stops before its session part ended is delivered as far as it goes and marked`() {
        recorder.beginJournal(clock.now(), journal::addAll)
        update("network", "wifi")

        // nothing more could be journaled, though the part went on to end
        val ended = sessionSpan.copy(startTimeNanos = SESSION_START_NANOS, endTimeNanos = SESSION_END_NANOS)
        val envelope = envelope(spans = listOf(ended)).withStateSpan(journal)

        assertNull(envelope.data.spanSnapshots)
        val stateSpan = checkNotNull(envelope.data.spans).last()
        assertEquals(SESSION_END_NANOS, stateSpan.endTimeNanos)
        assertEquals("true", stateSpan.attributeMap()["emb.states.truncated"])
        assertEquals(listOf("wifi"), stateSpan.newValues())
    }

    @Test
    fun `a journal with a change missing is delivered up to the missing change and marked`() {
        recorder.beginJournal(clock.now(), journal::addAll)
        update("network", "wifi")
        update("network", "wan")
        update("network", "none")
        recorder.endJournal(clock.now())
        val missingOne = journal.filterNot { it.change != null && it.sequence == 2L }

        val envelope = envelope(snapshots = listOf(sessionSpan)).withStateSpan(missingOne)

        // the end of the part is recorded after the missing change, so is not taken from the journal either
        val stateSpan = checkNotNull(envelope.data.spanSnapshots).last()
        assertNull(stateSpan.endTimeNanos)
        assertEquals("true", stateSpan.attributeMap()["emb.states.truncated"])
        assertEquals(listOf("wifi"), stateSpan.newValues())
    }

    @Test
    fun `a session part with no journal is delivered as it is`() {
        val envelope = envelope(spans = listOf(sessionSpan))
        assertSame(envelope, envelope.withStateSpan(emptyList()))
    }

    private fun update(state: String, value: Any, attributes: Map<String, String> = emptyMap()): Long {
        val time = clock.tick()
        recorder.onEvent(StateUpdate(state, value, time, attributes))
        return time
    }

    private fun envelope(spans: List<Span>? = null, snapshots: List<Span>? = null) =
        Envelope(data = SessionPartPayload(spans = spans, spanSnapshots = snapshots))

    private fun stateSpanOf(spans: List<Span>): Span = checkNotNull(envelope(spans = spans).withStateSpan(journal).data.spans).last()

    private fun Span.newValues(): List<String?> = checkNotNull(events).map { it.attributeMap()["emb.states.new_value"] }

    private fun Span.attributeMap(): Map<String?, String?> = checkNotNull(attributes).associate { it.key to it.data }

    private fun io.embrace.android.embracesdk.internal.payload.SpanEvent.attributeMap(): Map<String?, String?> =
        checkNotNull(attributes).associate { it.key to it.data }

    private object Backgrounded : SystemStateValue {
        override fun toString(): String = "Backgrounded"
    }

    private companion object {
        const val SESSION_START_NANOS = 5_000_000L
        const val SESSION_END_NANOS = 9_000_000L
    }
}
