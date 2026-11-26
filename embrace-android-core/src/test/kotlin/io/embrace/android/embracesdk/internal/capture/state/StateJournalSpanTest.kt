package io.embrace.android.embracesdk.internal.capture.state

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.arch.datasource.StateDeclaration
import io.embrace.android.embracesdk.internal.arch.datasource.StateUpdate
import io.embrace.android.embracesdk.internal.arch.schema.SystemStateValue
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.persistence.StatePartEnd
import io.embrace.android.embracesdk.internal.session.persistence.StateRecord
import io.embrace.android.embracesdk.internal.session.persistence.StateValue
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
        declare("screen", attributeKeys = setOf("key"))
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
                "emb.app.state.initial_seq" to "0",
                "emb.app.state.part_start_seq" to "3",
            ),
            stateSpan.attributeMap(),
        )

        val events = checkNotNull(stateSpan.events)
        assertEquals(
            listOf("emb-state-network", "emb-state-screen", "emb-state-network", "emb-state-screen"),
            events.map { it.name },
        )
        // an event carries its own number, which is its position after the journal's opening values
        assertEquals(listOf("1", "2", "3", "4"), events.map { it.attributeMap()["emb.app.state.seq"] })
        assertEquals(
            mapOf("emb.app.state.seq" to "2", "emb.app.state.new_value" to "Backgrounded", "emb.app.state.new_value_type" to "system"),
            events[1].attributeMap(),
        )
        // made before the part started, so it is earlier than the span itself
        assertEquals(heldChangeTime.millisToNanos(), events[2].timestampNanos)
        assertTrue(checkNotNull(events[2].timestampNanos) < checkNotNull(stateSpan.startTimeNanos))
        assertEquals(changeTime.millisToNanos(), events[3].timestampNanos)
        assertEquals(mapOf("key" to "value", "emb.app.state.seq" to "4", "emb.app.state.new_value" to "Home"), events[3].attributeMap())

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
        assertEquals("wifi", attributes["emb.app.state.initial_value.network"])
        assertNull(attributes["emb.app.state.initial_value_type.network"])
        assertEquals("Backgrounded", attributes["emb.app.state.initial_value.screen"])
        assertEquals("system", attributes["emb.app.state.initial_value_type.screen"])
        assertEquals("2", attributes["emb.app.state.initial_seq"])
        assertEquals("2", attributes["emb.app.state.part_start_seq"])
        // nothing says how the part ended, so nothing says the events lead to the values it ended with
        assertEquals("true", attributes["emb.app.state.truncated"])
        assertEquals(listOf("wan"), checkNotNull(stateSpan.events).map { it.attributeMap()["emb.app.state.new_value"] })
        assertEquals(listOf("3"), checkNotNull(stateSpan.events).map { it.attributeMap()["emb.app.state.seq"] })
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
            attributes = listOf(Attribute("emb.app.state.seq", "5")),
        )
        val beforeFirstChange = running.copy(name = "early", attributes = emptyList())
        val completed = otherSpan

        val envelope = envelope(spans = listOf(completed), snapshots = listOf(sessionSpan, running, beforeFirstChange))
            .withStateSpan(journal, completesRunningSpans = true)
        val snapshots = checkNotNull(envelope.data.spanSnapshots).associateBy { it.name }

        assertEquals("9", snapshots.getValue("running").attributeMap()["emb.app.state.end_seq"])
        // started before the first change, so every change the journal lists happened while it ran
        assertEquals("9", snapshots.getValue("early").attributeMap()["emb.app.state.end_seq"])
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
            attributes = listOf(Attribute("emb.app.state.seq", "5")),
        )

        val envelope = envelope(snapshots = listOf(sessionSpan, carried)).withStateSpan(journal, completesRunningSpans = true)

        assertEquals(
            "9",
            checkNotNull(envelope.data.spanSnapshots).first { it.name == "carried" }.attributeMap()["emb.app.state.end_seq"],
        )
    }

    @Test
    fun `a span a dead process left running is given the number of a change a limit refused, from the record of that limit`() {
        // the state's second change is refused, and nothing is listed after it before the process is killed
        recorder.beginJournal(clock.now(), journal::addAll)
        update("network", "wifi", maxChangesInPart = 1)
        update("network", "none", maxChangesInPart = 1)
        assertEquals(listOf(1L), journal.filter { it.change != null }.map(StateRecord::sequence))
        val running = Span(
            traceId = "c".repeat(32),
            spanId = "d".repeat(16),
            name = "running",
            attributes = listOf(Attribute("emb.app.state.seq", "1")),
        )

        val envelope = envelope(snapshots = listOf(sessionSpan, running)).withStateSpan(journal, completesRunningSpans = true)

        assertEquals(
            "2",
            checkNotNull(envelope.data.spanSnapshots).first { it.name == "running" }.attributeMap()["emb.app.state.end_seq"],
        )
    }

    @Test
    fun `a span with no number is given an end number only if another span of the part has one`() {
        recorder.beginJournal(clock.now(), journal::addAll)
        repeat(3) { update("network", "value-$it") }
        val unnumbered =
            Span(traceId = "c".repeat(32), spanId = "d".repeat(16), name = "unnumbered", attributes = emptyList())
        val numbered = unnumbered.copy(name = "numbered", attributes = listOf(Attribute("emb.app.state.seq", "1")))

        // no span has a number, as when association was off in the process that died
        val noneLinked = envelope(snapshots = listOf(sessionSpan, unnumbered)).withStateSpan(journal, completesRunningSpans = true)
        assertNull(
            checkNotNull(noneLinked.data.spanSnapshots).first {
                it.name == "unnumbered"
            }.attributeMap()["emb.app.state.end_seq"],
        )

        // another span has one, so the process was linking spans and this one started before the first change
        val someLinked = envelope(snapshots = listOf(sessionSpan, unnumbered, numbered))
            .withStateSpan(journal, completesRunningSpans = true)
        val delivered = checkNotNull(someLinked.data.spanSnapshots).associateBy { it.name }
        assertEquals("3", delivered.getValue("unnumbered").attributeMap()["emb.app.state.end_seq"])
        assertEquals("3", delivered.getValue("numbered").attributeMap()["emb.app.state.end_seq"])
    }

    @Test
    fun `a running span is given no end number if no change came after its start, or the part has no journal, or it is not asked for`() {
        recorder.beginJournal(clock.now(), journal::addAll)
        repeat(5) { update("network", "value-$it") }
        val running = Span(
            traceId = "c".repeat(32),
            spanId = "d".repeat(16),
            name = "running",
            attributes = listOf(Attribute("emb.app.state.seq", "5")),
        )
        val dead = envelope(snapshots = listOf(sessionSpan, running))

        // the journal ends at the number the span started at
        val noLaterChange = dead.withStateSpan(journal, completesRunningSpans = true)
        assertNull(
            checkNotNull(noLaterChange.data.spanSnapshots).first {
                it.name == "running"
            }.attributeMap()["emb.app.state.end_seq"],
        )

        // not asked for: the number is not given even though a change came after
        repeat(4) { update("network", "later-$it") }
        val notAsked = dead.withStateSpan(journal)
        assertNull(
            checkNotNull(notAsked.data.spanSnapshots).first {
                it.name == "running"
            }.attributeMap()["emb.app.state.end_seq"],
        )

        // no journal: the envelope is as it was
        assertSame(dead, dead.withStateSpan(emptyList(), completesRunningSpans = true))

        // a part that ended normally is not a dead one, whatever spans were still open
        recorder.endJournal(clock.now())
        val ended = envelope(spans = listOf(sessionSpan), snapshots = listOf(running))
            .withStateSpan(journal, completesRunningSpans = true)
        assertNull(
            checkNotNull(ended.data.spanSnapshots).first {
                it.name == "running"
            }.attributeMap()["emb.app.state.end_seq"],
        )
    }

    @Test
    fun `a journal that reaches the global limit states it, counts what each state lost, and states no limit of a state's own`() {
        recorder = StateRecorder(clock, maxChanges = 2)
        recorder.beginJournal(clock.now(), journal::addAll)
        update("network", "wifi")
        update("power", "low")
        update("network", "wan")
        recorder.endJournal(clock.now())

        val stateSpan = checkNotNull(envelope(spans = listOf(sessionSpan)).withStateSpan(journal).data.spans).last()
        val attributes = stateSpan.attributeMap()

        assertEquals(2, stateSpan.events?.size)
        assertEquals("1", attributes["emb.app.state.dropped_changes"])
        assertEquals("2", attributes["emb.app.state.global_change_limit"])
        // the state that lost a change is counted and given its final value, and its own limit was not what refused it
        assertEquals("1", attributes["emb.app.state.dropped_count.network"])
        assertEquals("wan", attributes["emb.app.state.final_value.network"])
        assertEquals("3", attributes["emb.app.state.final_seq.network"])
        assertNull(attributes["emb.app.state.change_limit.network"])
        // the other state lost nothing, so the events lead to its value
        assertEquals(emptyList<String?>(), attributes.keys.filter { it?.endsWith(".power") == true })
        assertNull(attributes["emb.app.state.truncated"])
    }

    @Test
    fun `a state past its limit has its count, its limit, its final value and the number of its last change on the span`() {
        recorder.beginJournal(clock.now(), journal::addAll)
        update("network", "wifi", maxChangesInPart = 1)
        update("network", "wan", maxChangesInPart = 1)
        update("power", "low", droppedChanges = 3)
        update("network", "none", maxChangesInPart = 1)
        update("screen", Backgrounded, maxChangesInPart = 0)
        update("power", "normal")
        recorder.endJournal(clock.now())

        val stateSpan = stateSpanOf(listOf(sessionSpan))
        val attributes = stateSpan.attributeMap()

        // the span is not cut at a gap in the numbers, which is where a limit refused a change, and is not marked
        assertEquals(listOf("wifi", "low", "normal"), stateSpan.newValues())
        assertEquals(listOf("1", "3", "6"), checkNotNull(stateSpan.events).map { it.attributeMap()["emb.app.state.seq"] })
        assertNull(attributes["emb.app.state.truncated"])

        // changes that took a number and are not listed, for every state together
        assertEquals("3", attributes["emb.app.state.dropped_changes"])
        assertNull(attributes["emb.app.state.global_change_limit"])
        assertEquals("2", attributes["emb.app.state.dropped_count.network"])
        assertEquals("1", attributes["emb.app.state.change_limit.network"])
        assertEquals("none", attributes["emb.app.state.final_value.network"])
        assertNull(attributes["emb.app.state.final_value_type.network"])
        // the number of its last change, which was refused: its final value holds from there, so only what is numbered
        // between the first gap and that number has no value for the state
        assertEquals("4", attributes["emb.app.state.final_seq.network"])
        // a limit that lists nothing is stated as well
        assertEquals("1", attributes["emb.app.state.dropped_count.screen"])
        assertEquals("0", attributes["emb.app.state.change_limit.screen"])
        assertEquals("Backgrounded", attributes["emb.app.state.final_value.screen"])
        assertEquals("system", attributes["emb.app.state.final_value_type.screen"])
        assertEquals("5", attributes["emb.app.state.final_seq.screen"])
        // what a reporter dropped is in its state's count alone: no limit refused it, and the events lead to the value
        assertEquals("3", attributes["emb.app.state.dropped_count.power"])
        assertNull(attributes["emb.app.state.change_limit.power"])
        assertNull(attributes["emb.app.state.final_value.power"])
        assertNull(attributes["emb.app.state.final_seq.power"])

        // the part ended, so all of that is read from its end: the records of the limits as they were reached add nothing
        assertEquals(2, journal.count { it.limit_reached != null })
        val withoutThem = envelope(spans = listOf(sessionSpan)).withStateSpan(journal.filter { it.limit_reached == null })
        assertEquals(stateSpan, checkNotNull(withoutThem.data.spans).last())
    }

    @Test
    fun `a reader that follows the rule for a state past its limit takes its value where it is known, and never a wrong one`() {
        declare("network", maxChangesInPart = 2)
        declare("power")
        // what every state held after each numbered change, to check the reader against
        val held = HashMap<Long, Map<String, String>>()
        fun change(state: String, value: String) {
            update(state, value)
            val snapshot = recorder.snapshot()
            held[snapshot.sequence] = snapshot.values.mapValues { it.value.value_ }
        }
        recorder.beginJournal(clock.now(), journal::addAll)
        change("network", "wifi")
        change("power", "low")
        change("network", "wan")
        // the network is past its limit of two from here: both of these are refused, and the second is its last change
        change("network", "none")
        change("power", "normal")
        change("network", "wifi")
        change("power", "low")
        recorder.endJournal(clock.now())

        val stateSpan = stateSpanOf(listOf(sessionSpan))
        val attributes = stateSpan.attributeMap()
        val listed = checkNotNull(stateSpan.events).map { event ->
            val eventAttributes = event.attributeMap()
            Triple(
                checkNotNull(eventAttributes["emb.app.state.seq"]).toLong(),
                checkNotNull(event.name).removePrefix("emb-state-"),
                checkNotNull(eventAttributes["emb.app.state.new_value"]),
            )
        }
        val numbers = 1L..7L
        val firstGap = numbers.first { number -> listed.none { it.first == number } }

        // the rule: a state with a final number has its final value from that number on, and no value between the first
        // gap in the log and that number. Below the gap it has what the listed changes lead to, as a state with no
        // count has at every number
        fun valueAt(state: String, number: Long): String? {
            val finalSequence = attributes["emb.app.state.final_seq.$state"]?.toLong()
            return when {
                finalSequence != null && number >= finalSequence -> attributes["emb.app.state.final_value.$state"]
                finalSequence != null && number >= firstGap -> NO_VALUE
                else -> listed.lastOrNull { it.first <= number && it.second == state }?.third
            }
        }

        assertEquals(4L, firstGap)
        assertEquals("6", attributes["emb.app.state.final_seq.network"])
        assertNull(attributes["emb.app.state.final_seq.power"])
        assertEquals(
            listOf("wifi", "wifi", "wan", NO_VALUE, NO_VALUE, "wifi", "wifi"),
            numbers.map { valueAt("network", it) },
        )
        assertEquals(listOf(null, "low", "low", "low", "normal", "normal", "low"), numbers.map { valueAt("power", it) })
        // and wherever the reader takes a value, it is the one the state held
        numbers.forEach { number ->
            listOf("network", "power").forEach { state ->
                val taken = valueAt(state, number)
                if (taken != NO_VALUE) {
                    assertEquals("$state at $number", held.getValue(number)[state], taken)
                }
            }
        }
    }

    @Test
    fun `a journal whose part did not end states each limit that was reached, and has no count and no final value`() {
        recorder.beginJournal(clock.now(), journal::addAll)
        update("network", "wifi", maxChangesInPart = 1)
        update("network", "wan", maxChangesInPart = 1)
        update("power", "low")
        update("network", "none", maxChangesInPart = 1)

        // the process died here: the part's end, with its counts and final values, was never recorded
        val stateSpan = checkNotNull(envelope(snapshots = listOf(sessionSpan)).withStateSpan(journal).data.spanSnapshots).last()
        val attributes = stateSpan.attributeMap()

        assertEquals("true", attributes["emb.app.state.truncated"])
        assertEquals(listOf("1", "3"), checkNotNull(stateSpan.events).map { it.attributeMap()["emb.app.state.seq"] })
        // the limit says whose changes the gap can be: the other state has every change listed up to the last event
        assertEquals("1", attributes["emb.app.state.change_limit.network"])
        assertEquals(listOf("emb.app.state.change_limit.network"), attributes.keys.filter { it?.contains("limit") == true })
        assertEquals(
            emptyList<String?>(),
            attributes.keys.filter { it?.contains("dropped") == true || it?.contains("final_") == true },
        )

        // the same for the limit every state shares, which names no state
        recorder = StateRecorder(clock, maxChanges = 1)
        val shared = mutableListOf<StateRecord>()
        recorder.beginJournal(clock.now(), shared::addAll)
        update("network", "wifi")
        update("power", "low")
        update("network", "wan")
        val sharedAttributes = checkNotNull(envelope(snapshots = listOf(sessionSpan)).withStateSpan(shared).data.spanSnapshots)
            .last()
            .attributeMap()
        assertEquals("true", sharedAttributes["emb.app.state.truncated"])
        assertEquals("1", sharedAttributes["emb.app.state.global_change_limit"])
        assertEquals(
            listOf("emb.app.state.global_change_limit"),
            sharedAttributes.keys.filter {
                it?.contains("limit") == true
            },
        )
    }

    @Test
    fun `a journal written before states had limits of their own is delivered with its one count and every final value`() {
        recorder.beginJournal(clock.now(), journal::addAll)
        update("network", "wifi")
        update("power", "low")
        // as the part end was written then: one count for the part, and the value of every state
        val written = journal + StateRecord(
            sequence = 5,
            time_ms = clock.now(),
            part_end = StatePartEnd(
                dropped_changes = 3,
                values = listOf(StateValue(state = "network", value_ = "none"), StateValue(state = "power", value_ = "normal")),
            ),
        )
        // read back from the bytes it was written as
        val readBack = written.map { StateRecord.ADAPTER.decode(it.encode()) }

        val attributes = checkNotNull(envelope(spans = listOf(sessionSpan)).withStateSpan(readBack).data.spans).last().attributeMap()

        assertEquals("3", attributes["emb.app.state.dropped_changes"])
        assertEquals("none", attributes["emb.app.state.final_value.network"])
        assertEquals("normal", attributes["emb.app.state.final_value.power"])
        assertEquals(emptyList<String?>(), attributes.keys.filter { it?.startsWith("emb.app.state.dropped_count.") == true })
        assertEquals(emptyList<String?>(), attributes.keys.filter { it?.contains("limit") == true })
        assertEquals(emptyList<String?>(), attributes.keys.filter { it?.contains("final_seq") == true })
        assertNull(attributes["emb.app.state.truncated"])
    }

    @Test
    fun `changes folded in before the part and changes dropped during it are counted apart`() {
        recorder = StateRecorder(clock, maxChanges = 2, maxHeldChanges = 1)
        update("network", "wifi")
        update("network", "wan")
        recorder.beginJournal(clock.now(), journal::addAll)
        update("network", "none")
        update("screen", Backgrounded)
        recorder.endJournal(clock.now())

        val stateSpan = stateSpanOf(listOf(sessionSpan))
        val attributes = stateSpan.attributeMap()

        // the first change is behind the values the span opens with, and the last is past the changes it holds
        assertEquals("1", attributes["emb.app.state.folded_changes"])
        assertEquals("1", attributes["emb.app.state.initial_seq"])
        assertEquals("wifi", attributes["emb.app.state.initial_value.network"])
        assertEquals(listOf("wan", "none"), stateSpan.newValues())
        // the numbers follow initial_seq, and the dropped change is not among them
        assertEquals(listOf("2", "3"), checkNotNull(stateSpan.events).map { it.attributeMap()["emb.app.state.seq"] })
        assertEquals("1", attributes["emb.app.state.dropped_changes"])
        // the value the part ended with is given for the state that had a change dropped, and not for the other
        assertEquals("Backgrounded", attributes["emb.app.state.final_value.screen"])
        assertEquals("system", attributes["emb.app.state.final_value_type.screen"])
        assertNull(attributes["emb.app.state.final_value.network"])
        assertNull(attributes["emb.app.state.truncated"])
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

        assertEquals("0", attributes["emb.app.state.initial_seq"])
        assertEquals("2", attributes["emb.app.state.part_start_seq"])
        assertEquals("true", attributes["emb.app.state.truncated"])
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
        assertNull(first.attributeMap()["emb.app.state.truncated"])
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
        assertEquals("true", stateSpan.attributeMap()["emb.app.state.truncated"])
        assertEquals(listOf("wifi"), stateSpan.newValues())
    }

    @Test
    fun `a journal is delivered whole across a gap in its changes' numbers, and up to a change that is out of order`() {
        recorder.beginJournal(clock.now(), journal::addAll)
        update("network", "wifi")
        update("network", "wan")
        update("network", "none")
        recorder.endJournal(clock.now())

        // a number that skips is a change that is not listed, which a limit can leave anywhere, and nothing is cut there
        val skipping = journal.filterNot { it.change != null && it.sequence == 2L }
        val whole = checkNotNull(envelope(spans = listOf(sessionSpan)).withStateSpan(skipping).data.spans).last()
        assertEquals(listOf("wifi", "none"), whole.newValues())
        assertNull(whole.attributeMap()["emb.app.state.truncated"])

        // a change numbered at or below the one before it is not something a journal is written with
        val outOfOrder = journal.filter { it.change == null || it.sequence != 2L }.toMutableList().apply {
            add(indexOfFirst { it.part_end != null }, journal.first { it.change != null && it.sequence == 2L })
        }
        val envelope = envelope(snapshots = listOf(sessionSpan)).withStateSpan(outOfOrder)

        // the end of the part is recorded after that change, so is not taken from the journal either
        val stateSpan = checkNotNull(envelope.data.spanSnapshots).last()
        assertNull(stateSpan.endTimeNanos)
        assertEquals("true", stateSpan.attributeMap()["emb.app.state.truncated"])
        assertEquals(listOf("wifi", "none"), stateSpan.newValues())
    }

    @Test
    fun `a session part with no journal is delivered as it is`() {
        val envelope = envelope(spans = listOf(sessionSpan))
        assertSame(envelope, envelope.withStateSpan(emptyList()))
    }

    /**
     * Declares [state] to the recorder, as its data source does when its capture starts. The first declaration of a name
     * is the one that stands.
     */
    private fun declare(
        state: String,
        maxChangesInPart: Int = StateDeclaration.DEFAULT_MAX_CHANGES_IN_PART,
        attributeKeys: Set<String> = emptySet(),
    ) {
        recorder.declare(StateDeclaration(state, maxChangesInPart, attributeKeys))
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
        droppedChanges: Int = 0,
    ): Long {
        declare(state, maxChangesInPart)
        val time = clock.tick()
        recorder.onEvent(StateUpdate(state, value, time, attributes, droppedChanges))
        return time
    }

    private fun envelope(spans: List<Span>? = null, snapshots: List<Span>? = null) =
        Envelope(data = SessionPartPayload(spans = spans, spanSnapshots = snapshots))

    private fun stateSpanOf(spans: List<Span>): Span = checkNotNull(envelope(spans = spans).withStateSpan(journal).data.spans).last()

    private fun Span.newValues(): List<String?> = checkNotNull(events).map { it.attributeMap()["emb.app.state.new_value"] }

    private fun Span.attributeMap(): Map<String?, String?> = checkNotNull(attributes).associate { it.key to it.data }

    private fun io.embrace.android.embracesdk.internal.payload.SpanEvent.attributeMap(): Map<String?, String?> =
        checkNotNull(attributes).associate { it.key to it.data }

    private object Backgrounded : SystemStateValue {
        override fun toString(): String = "Backgrounded"
    }

    private companion object {
        const val SESSION_START_NANOS = 5_000_000L
        const val SESSION_END_NANOS = 9_000_000L

        /**
         * What a reader of a change log has for a state at a number where the log does not say its value.
         */
        const val NO_VALUE = "no value"
    }
}
