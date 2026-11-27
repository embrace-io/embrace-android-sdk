package io.embrace.android.embracesdk.internal.capture.state

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.TestState
import io.embrace.android.embracesdk.internal.arch.datasource.StateDataSource
import io.embrace.android.embracesdk.internal.arch.datasource.StateDeclaration
import io.embrace.android.embracesdk.internal.arch.datasource.StateUpdate
import io.embrace.android.embracesdk.internal.arch.schema.SystemStateValue
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.resolved.AutoDataCaptureConfig
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.config.resolved.PersistenceConfig
import io.embrace.android.embracesdk.internal.session.persistence.StatePartEnd
import io.embrace.android.embracesdk.internal.session.persistence.StatePartStart
import io.embrace.android.embracesdk.internal.session.persistence.StateRecord
import io.embrace.android.embracesdk.internal.session.persistence.StateValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal class StateRecorderTest {

    private lateinit var clock: FakeClock
    private lateinit var recorder: StateRecorder
    private lateinit var journal: MutableList<StateRecord>
    private var changesDropped = 0
    private lateinit var stateLimitsApplied: MutableList<String>

    @Before
    fun setUp() {
        clock = FakeClock()
        recorder = StateRecorder(clock)
        journal = mutableListOf()
        changesDropped = 0
        stateLimitsApplied = mutableListOf()
    }

    @Test
    fun `changes of every state share one order and unchanged values are not changes`() {
        declare("network", attributeKeys = setOf("key"))
        recorder.beginJournal(clock.now(), journal::addAll)
        update("network", "wifi")
        update("power", "low")
        update("network", "wifi")
        update("network", "wan", attributes = mapOf("key" to "value"))

        val changes = journal.mapNotNull(StateRecord::change)
        val changed = changes.map { "${it.value_?.state}=${it.value_?.value_}" }
        assertEquals(listOf("network=wifi", "power=low", "network=wan"), changed)
        assertEquals(listOf(1L, 2L, 3L), journal.filter { it.change != null }.map(StateRecord::sequence))
        assertEquals(mapOf("key" to "value"), changes.last().attributes)

        val snapshot = recorder.snapshot()
        assertEquals(3L, snapshot.sequence)
        assertEquals("network=wan;power=low", snapshot.encoded)
    }

    @Test
    fun `a snapshot marks system values and escapes what would break its encoding`() {
        assertTrue(recorder.snapshot().isEmpty())
        update("screen", Backgrounded)
        update("route", "a;b=c~d\\e")

        assertEquals("screen~Backgrounded;route=a\\;b\\=c\\~d\\\\e", recorder.snapshot().encoded)
    }

    @Test
    fun `a snapshot gives every value as an attribute, says what differs from an earlier one, and when it was recorded`() {
        recorder = StateRecorder(clock, keepRecent = true)
        assertEquals(emptyMap<String, String>(), recorder.snapshot().attributes)
        assertEquals(0L, recorder.snapshot().sinceMs)
        update("screen", Backgrounded)
        update("network", "wifi")
        val earlier = recorder.snapshot()
        val earlierTime = clock.now()
        update("power", "low")
        clock.tick(10)
        update("network", "none")
        update("screen", Backgrounded)

        val snapshot = recorder.snapshot()
        assertEquals(
            mapOf(
                "emb.app.state.value.screen" to "Backgrounded",
                "emb.app.state.value_type.screen" to "system",
                "emb.app.state.value.network" to "none",
                "emb.app.state.value.power" to "low",
            ),
            snapshot.attributes,
        )
        // built once for a snapshot, so that everything stamped until the next change shares it
        assertTrue(snapshot.attributes === snapshot.attributes)
        assertEquals(
            mapOf(
                "emb.app.state.value.screen" to "Backgrounded",
                "emb.app.state.value_type.screen" to "system",
                "emb.app.state.value.network" to "wifi",
            ),
            earlier.attributes,
        )

        // a state that has its first value differs from having none
        assertEquals(
            mapOf("emb.app.state.end_value.network" to "none", "emb.app.state.end_value.power" to "low"),
            snapshot.endAttributes(earlier),
        )
        assertEquals(
            mapOf(
                "emb.app.state.end_value.screen" to "Backgrounded",
                "emb.app.state.end_value_type.screen" to "system",
                "emb.app.state.end_value.network" to "none",
                "emb.app.state.end_value.power" to "low",
            ),
            snapshot.endAttributes(StateSnapshot.NONE),
        )
        assertEquals(emptyMap<String, String>(), snapshot.endAttributes(snapshot))

        // an update that changed nothing does not move the time either
        assertEquals(earlierTime, earlier.sinceMs)
        assertEquals(clock.now() - 1, snapshot.sinceMs)
    }

    @Test
    fun `the snapshot that held at a recent time is remembered, and a time further back than the last 64 is not known`() {
        recorder = StateRecorder(clock, keepRecent = true)
        val beforeAnyChange = clock.now()
        clock.tick(10)
        val firstChange = update("network", "wifi")
        clock.tick(10)
        val secondChange = update("network", "none")
        // a second change in the same millisecond
        declare("power")
        recorder.onEvent(StateUpdate("power", "low", secondChange))
        clock.tick(10)

        // no state had a value before the first change, however long before
        assertEquals(0L, recorder.snapshotAt(beforeAnyChange)?.sequence)
        assertEquals(0L, recorder.snapshotAt(0)?.sequence)
        assertEquals(0L, recorder.snapshotAt(firstChange - 1)?.sequence)
        // a time equal to the time of a change is after that change, and after every change made at that time
        assertEquals("network=wifi", recorder.snapshotAt(firstChange)?.encoded)
        assertEquals(1L, recorder.snapshotAt(secondChange - 1)?.sequence)
        assertEquals(3L, recorder.snapshotAt(secondChange)?.sequence)
        assertSame(recorder.snapshot(), recorder.snapshotAt(clock.now()))

        // 64 more changes, which leaves the last 64 remembered: the oldest that was is now the one after `power`
        val changeTimes = (0 until StateRecorder.RECENT_SNAPSHOTS).map {
            clock.tick(10)
            update("network", "value-$it")
        }
        assertEquals(67L, recorder.snapshot().sequence)
        assertNull(recorder.snapshotAt(secondChange))
        assertNull(recorder.snapshotAt(changeTimes.first() - 1))
        assertEquals(4L, recorder.snapshotAt(changeTimes.first())?.sequence)
        assertEquals("network=value-0;power=low", recorder.snapshotAt(changeTimes[1] - 1)?.encoded)
        assertEquals(67L, recorder.snapshotAt(changeTimes.last())?.sequence)
    }

    @Test
    fun `a recorder that remembers nothing does not read the clock for a change, and knows no past snapshot`() {
        var clockReads = 0
        val countingClock = Clock {
            clockReads++
            clock.now()
        }
        recorder = StateRecorder(countingClock)
        val readsToCreate = clockReads
        update("network", "wifi")
        recorder.beginJournal(clock.now(), journal::addAll)
        update("network", "none")
        assertEquals(readsToCreate, clockReads)
        assertEquals(0L, recorder.snapshot().sinceMs)
        assertNull(recorder.snapshotAt(clock.now()))

        // one that remembers reads it once for each change, and not for an update that changes nothing
        recorder = StateRecorder(countingClock, keepRecent = true)
        val readsBefore = clockReads
        update("network", "wifi")
        update("network", "wifi")
        update("network", "none")
        assertEquals(readsBefore + 2, clockReads)
    }

    @Test
    fun `a past snapshot is read without waiting for a change that is being recorded`() {
        val recording = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val listener = object : StateChangeListener {
            override fun onChange(record: StateRecord, snapshot: StateSnapshot, previous: StateValue?) {
                // called under the recorder's lock, which is held for as long as this waits
                recording.countDown()
                resume.await(WAIT_SECONDS, TimeUnit.SECONDS)
            }

            override fun onPartStart(timeMs: Long, snapshot: StateSnapshot) {
            }
        }
        recorder = StateRecorder(clock, listener = listener, keepRecent = true)
        recorder.beginJournal(clock.now()) { }
        val reporter = thread { update("network", "wifi") }
        assertTrue(recording.await(WAIT_SECONDS, TimeUnit.SECONDS))

        val reader = Executors.newSingleThreadExecutor()
        try {
            val read = reader.submit<Pair<Long?, Long>> {
                recorder.snapshotAt(clock.now())?.sequence to recorder.snapshot().sequence
            }
            assertEquals(1L to 1L, read.get(WAIT_SECONDS, TimeUnit.SECONDS))
        } finally {
            resume.countDown()
            reader.shutdown()
            reporter.join()
        }
    }

    @Test
    fun `a listener is told of each change a journal lists as it is made, with the values after it, and of each part that starts`() {
        val told = mutableListOf<String>()
        val listener = object : StateChangeListener {
            override fun onChange(record: StateRecord, snapshot: StateSnapshot, previous: StateValue?) {
                assertEquals(record.sequence, snapshot.sequence)
                told.add("${record.sequence}:${snapshot.encoded}")
            }

            override fun onPartStart(timeMs: Long, snapshot: StateSnapshot) {
                told.add("part@$timeMs:${snapshot.sequence}:${snapshot.encoded}")
            }
        }
        recorder = StateRecorder(clock, maxChanges = 2, listener = listener)

        // with no part in progress a change is not reported
        update("network", "wifi")
        update("network", "wan")
        assertEquals(emptyList<String>(), told)

        // the start of a part is reported with the values those changes left
        val partStart = clock.tick(1000)
        recorder.beginJournal(partStart, journal::addAll)
        update("power", "low")
        update("power", "low")
        update("network", "none")
        // the journal is full: these are counted, and not recorded or reported
        update("power", "normal")
        update("network", "wifi")
        assertEquals(
            listOf(
                "part@$partStart:2:network=wan",
                "3:network=wan;power=low",
                "4:network=none;power=low",
            ),
            told,
        )
        assertEquals(6L, recorder.snapshot().sequence)
    }

    @Test
    fun `a change made with no journal open takes a number and replaces the value, is counted for its state, and is kept nowhere`() {
        val told = mutableListOf<String>()
        val listener = object : StateChangeListener {
            override fun onChange(record: StateRecord, snapshot: StateSnapshot, previous: StateValue?) {
                told.add("${record.sequence}:${snapshot.encoded}")
            }

            override fun onPartStart(timeMs: Long, snapshot: StateSnapshot) {
                told.add("part:${snapshot.sequence}")
            }
        }
        recorder = StateRecorder(clock, listener = listener, keepRecent = true)
        // a state's first value counts like any other change, and an update that repeats the value is no change
        update("network", "wifi")
        clock.tick(10)
        val secondChange = update("network", "wan")
        update("network", "wan")
        clock.tick(10)
        update("power", "low")

        // each took its number and replaced the value, so a stamp is right, and so is the value at a recent time
        assertEquals(3L, recorder.snapshot().sequence)
        assertEquals("network=wan;power=low", recorder.snapshot().encoded)
        assertEquals("network=wan", recorder.snapshotAt(secondChange)?.encoded)
        assertEquals(2L, recorder.snapshotAt(secondChange)?.sequence)
        // none was handed to a journal, as there was none, and none to the listener
        assertTrue(journal.isEmpty())
        assertEquals(emptyList<String>(), told)

        // the next journal opens with every state's value as it stands, as of the last number, and its start carries
        // the count for each state. It lists none of those changes.
        val partStart = clock.tick(1000)
        recorder.beginJournal(partStart, journal::addAll)
        assertEquals(2, journal.size)
        with(journal[0]) {
            assertEquals(3L, sequence)
            assertEquals(partStart, time_ms)
            assertEquals(listOf("network=wan", "power=low"), checkNotNull(checkpoint).values.map { "${it.state}=${it.value_}" })
        }
        with(journal[1]) {
            assertEquals(3L, sequence)
            assertEquals(partStart, time_ms)
            assertEquals(listOf("network:2", "power:1"), checkNotNull(part_start).notInSession())
        }
        assertEquals(listOf("part:3"), told)

        update("network", "none")
        val partEnd = clock.tick(1000)
        recorder.endJournal(partEnd)
        assertEquals(listOf(4L), journal.filter { it.change != null }.map(StateRecord::sequence))
        with(journal.last()) {
            assertNotNull(part_end)
            assertEquals(4L, sequence)
            assertEquals(partEnd, time_ms)
            assertEquals(0, checkNotNull(part_end).dropped_changes)
        }

        // a change made after a journal ended is in no journal. The counts start again with each journal: the next one
        // counts what changed since this one ended, and opens with the values as they then stand.
        update("power", "normal")
        assertEquals(listOf(4L), journal.filter { it.change != null }.map(StateRecord::sequence))
        val nextJournal = mutableListOf<StateRecord>()
        recorder.beginJournal(clock.tick(1000), nextJournal::addAll)
        with(nextJournal[0]) {
            assertEquals(5L, sequence)
            assertEquals(
                listOf("network=none", "power=normal"),
                checkNotNull(checkpoint).values.map { "${it.state}=${it.value_}" },
            )
        }
        assertEquals(listOf("power:1"), checkNotNull(nextJournal[1].part_start).notInSession())
        recorder.endJournal(clock.now())

        // and a journal that begins with nothing changed since the last one ended has no count
        val lastJournal = mutableListOf<StateRecord>()
        recorder.beginJournal(clock.now(), lastJournal::addAll)
        assertEquals(5L, lastJournal[0].sequence)
        assertEquals(emptyList<String>(), checkNotNull(lastJournal[1].part_start).notInSession())
    }

    @Test
    fun `a journal that reaches its limit stops recording changes and still ends with the right values`() {
        recorder = StateRecorder(
            clock,
            maxChanges = 2,
            onChangeDropped = { changesDropped++ },
            onStateLimitApplied = { stateLimitsApplied += it },
        )
        recorder.beginJournal(clock.now(), journal::addAll)
        update("network", "wifi")
        update("network", "wan")
        update("network", "none")
        update("power", "low")

        assertEquals(2, journal.count { it.change != null })
        assertEquals("network=none;power=low", recorder.snapshot().encoded)
        assertEquals(4L, recorder.snapshot().sequence)
        // the limit is recorded as it first refuses a change, with that change's number, and once however many it refuses
        assertEquals(listOf("null:2@3"), journal.limitsReached())

        recorder.endJournal(clock.now())
        with(checkNotNull(journal.last().part_end)) {
            assertEquals(2, dropped_changes)
            assertEquals(listOf("network=none", "power=low"), values.map { "${it.state}=${it.value_}" })
            // the limit that refused them is the journal's, which is stated, and no state reached its own
            assertEquals(2, global_limit)
            assertEquals(listOf("network:1:null", "power:1:null"), counts())
            // each state that lost a change says the number of its last change, which its final value holds from
            assertEquals(listOf("network@3", "power@4"), finalSequences())
        }

        assertEquals(2, changesDropped)
        assertEquals(emptyList<String>(), stateLimitsApplied)

        // each journal has the limit to itself
        val nextJournal = mutableListOf<StateRecord>()
        recorder.beginJournal(clock.now(), nextJournal::addAll)
        update("power", "normal")
        assertEquals(1, nextJournal.count { it.change != null })
        assertEquals(emptyList<String>(), checkNotNull(nextJournal.single { it.part_start != null }.part_start).notInSession())
        recorder.endJournal(clock.now())
        assertEquals(emptyList<String>(), nextJournal.limitsReached())
        with(checkNotNull(nextJournal.last().part_end)) {
            assertEquals(0, dropped_changes)
            assertNull(global_limit)
            assertEquals(emptyList<Any>(), dropped_counts)
            assertEquals(emptyList<Any>(), values)
        }
    }

    @Test
    fun `a state that reaches its own limit is no longer listed while the others are, and its value stays right`() {
        val told = mutableListOf<Long>()
        val listener = object : StateChangeListener {
            override fun onChange(record: StateRecord, snapshot: StateSnapshot, previous: StateValue?) {
                told += record.sequence
            }

            override fun onPartStart(timeMs: Long, snapshot: StateSnapshot) {
            }
        }
        recorder = StateRecorder(
            clock,
            onChangeDropped = { changesDropped++ },
            onStateLimitApplied = { stateLimitsApplied += it },
            listener = listener,
        )
        // changes made before the part are counted and not listed, and use up none of the state's limit for the part
        repeat(3) { update("network", "before-$it", maxChangesInPart = 2) }
        recorder.beginJournal(clock.now(), journal::addAll)
        update("network", "wifi", maxChangesInPart = 2)
        update("network", "wan", maxChangesInPart = 2)
        val firstRefusedAt = update("network", "none", maxChangesInPart = 2)
        update("power", "low")
        update("network", "wifi", maxChangesInPart = 2)
        update("power", "normal")

        // the refused changes keep their numbers, so the numbers of the changes listed skip in the middle
        assertEquals(listOf(4L, 5L, 7L, 9L), journal.filter { it.change != null }.map(StateRecord::sequence))
        assertEquals(listOf(4L, 5L, 7L, 9L), told)
        assertEquals(listOf("network:3"), checkNotNull(journal[1].part_start).notInSession())
        // the limit is recorded where it first refuses a change, with that change's number and time, and not again for
        // the next change it refuses, nor for a change that is listed. It is no change, and the listener is not told.
        assertEquals(listOf("network:2@6"), journal.limitsReached())
        val reached = journal.indexOfFirst { it.limit_reached != null }
        assertEquals(5L to 7L, journal[reached - 1].sequence to journal[reached + 1].sequence)
        assertEquals(firstRefusedAt, journal[reached].time_ms)
        // and the value is the last one reported, listed or not
        assertEquals("network=wifi;power=normal", recorder.snapshot().encoded)
        assertEquals(9L, recorder.snapshot().sequence)

        recorder.endJournal(clock.now())
        with(journal.last()) {
            assertEquals(9L, sequence)
            with(checkNotNull(part_end)) {
                assertEquals(2, dropped_changes)
                // the count, the limit and the value the part ended with, for the state that reached its limit and no other
                assertEquals(listOf("network:2:2"), counts())
                assertEquals(listOf("network=wifi"), values.map { "${it.state}=${it.value_}" })
                // and the number of its last change, the second one refused, from which that value holds
                assertEquals(listOf("network@8"), finalSequences())
                assertNull(global_limit)
            }
        }
        // tracked for the state, and not as the journal's limit is
        assertEquals(listOf("network", "network"), stateLimitsApplied)
        assertEquals(0, changesDropped)

        // the count starts again with the next part
        val nextJournal = mutableListOf<StateRecord>()
        recorder.beginJournal(clock.now(), nextJournal::addAll)
        update("network", "wan", maxChangesInPart = 2)
        update("network", "none", maxChangesInPart = 2)
        update("network", "wifi", maxChangesInPart = 2)
        recorder.endJournal(clock.now())
        assertEquals(listOf(10L, 11L), nextJournal.filter { it.change != null }.map(StateRecord::sequence))
        with(checkNotNull(nextJournal.last().part_end)) {
            assertEquals(1, dropped_changes)
            assertEquals(listOf("network:1:2"), counts())
        }
        // and the limit is recorded again in the part it is reached in again
        assertEquals(listOf("network:2@12"), nextJournal.limitsReached())
    }

    @Test
    fun `a state declared with no limit of its own lists 100 changes in a session part`() {
        recorder.declare(StateDeclaration("network"))
        recorder.beginJournal(clock.now(), journal::addAll)
        repeat(StateDeclaration.DEFAULT_MAX_CHANGES_IN_PART + 1) { update("network", "value-$it") }
        update("power", "low")
        recorder.endJournal(clock.now())

        assertEquals(100, journal.count { it.change?.value_?.state == "network" })
        assertEquals("network=value-100;power=low", recorder.snapshot().encoded)
        with(checkNotNull(journal.last().part_end)) {
            assertEquals(1, dropped_changes)
            assertEquals(listOf("network:1:100"), counts())
            assertEquals(listOf("network=value-100"), values.map { "${it.state}=${it.value_}" })
        }
    }

    @Test
    fun `changes a reporter says it dropped are counted for their state in the part they belong to, and a repeated value is not`() {
        recorder.beginJournal(clock.now(), journal::addAll)
        update("network", "wifi")
        update("power", "low", droppedChanges = 2)
        // a repeated value is no change, and loses nothing, so it is counted nowhere
        update("network", "wifi")
        // one that comes with a count is no change either, and its count is taken
        update("power", "low", droppedChanges = 3)
        assertEquals(2L, recorder.snapshot().sequence)
        recorder.endJournal(clock.now())

        assertEquals(listOf(1L, 2L), journal.filter { it.change != null }.map(StateRecord::sequence))
        with(checkNotNull(journal.last().part_end)) {
            // a reported drop took no number, so it is in its state's count and not in the count for the part
            assertEquals(0, dropped_changes)
            assertEquals(listOf("power:5:null"), counts())
            // the changes listed still lead to the last value reported, so none is given, and no number it holds from
            assertEquals(emptyList<Any>(), values)
            assertEquals(listOf("power@null"), finalSequences())
        }

        // reported with no part in progress, it is counted in the next part's journal, and in no part after that
        update("power", "low", droppedChanges = 4)
        val nextJournal = mutableListOf<StateRecord>()
        recorder.beginJournal(clock.now(), nextJournal::addAll)
        update("power", "normal", droppedChanges = 1)
        recorder.endJournal(clock.now())
        assertEquals(listOf("power:5:null"), checkNotNull(nextJournal.last().part_end).counts())
        // what a reporter dropped is no part of the count of changes made with no journal open
        assertEquals(emptyList<String>(), checkNotNull(nextJournal[1].part_start).notInSession())

        val lastJournal = mutableListOf<StateRecord>()
        recorder.beginJournal(clock.now(), lastJournal::addAll)
        recorder.endJournal(clock.now())
        assertEquals(emptyList<Any>(), checkNotNull(lastJournal.last().part_end).dropped_counts)
    }

    @Test
    fun `a reporter that says it dropped a great many changes cannot wrap its state's count round to nothing`() {
        recorder.beginJournal(clock.now(), journal::addAll)
        update("power", "low", maxChangesInPart = 1, droppedChanges = Int.MAX_VALUE)
        update("power", "normal", maxChangesInPart = 1, droppedChanges = 5)
        recorder.endJournal(clock.now())

        with(checkNotNull(journal.last().part_end)) {
            assertEquals(1, dropped_changes)
            // the count holds at the most it can, and the limit that refused the second change is still stated
            assertEquals(listOf("power:${Int.MAX_VALUE}:1"), counts())
            assertEquals(listOf("power=normal"), values.map { "${it.state}=${it.value_}" })
        }
    }

    @Test
    fun `a change is counted as refused before anything is told of it, so a callback that throws leaves the counts right`() {
        recorder = StateRecorder(clock, onStateLimitApplied = { error("told of a limit") })
        recorder.beginJournal(clock.now(), journal::addAll)
        update("power", "low", maxChangesInPart = 1)
        runCatching { update("power", "normal", maxChangesInPart = 1) }
        recorder.endJournal(clock.now())

        assertEquals("normal", recorder.snapshot().values["power"]?.value_)
        with(checkNotNull(journal.last().part_end)) {
            assertEquals(1, dropped_changes)
            assertEquals(listOf("power:1:1"), counts())
            assertEquals(listOf("power=normal"), values.map { "${it.state}=${it.value_}" })
        }
    }

    @Test
    fun `a part in which a state's own limit and the global limit each refuse a change states both`() {
        recorder = StateRecorder(clock, maxChanges = 2, onChangeDropped = { changesDropped++ })
        recorder.beginJournal(clock.now(), journal::addAll)
        update("power", "low", maxChangesInPart = 1)
        // refused by the state's own limit, so it uses up none of what the journal as a whole may list
        update("power", "normal", maxChangesInPart = 1)
        update("network", "wifi")
        // refused by the limit on the journal as a whole
        update("network", "none")
        recorder.endJournal(clock.now())

        assertEquals(listOf(1L, 3L), journal.filter { it.change != null }.map(StateRecord::sequence))
        // each limit is recorded as it is reached, and the record of the first used up none of what the journal may list
        assertEquals(listOf("power:1@2", "null:2@4"), journal.limitsReached())
        with(checkNotNull(journal.last().part_end)) {
            assertEquals(2, dropped_changes)
            assertEquals(listOf("power:1:1", "network:1:null"), counts())
            assertEquals(2, global_limit)
            assertEquals(listOf("power=normal", "network=none"), values.map { "${it.state}=${it.value_}" }.sortedDescending())
        }
        assertEquals(1, changesDropped)
    }

    @Test
    fun `changes made with no journal open use up none of the global limit, which counts a part's own changes`() {
        recorder = StateRecorder(clock, maxChanges = 2, onChangeDropped = { changesDropped++ })
        // more changes with no journal open than a journal may list: none is refused, as none is kept
        update("network", "wifi")
        update("power", "low")
        update("network", "wan")
        update("network", "none")
        assertEquals(0, changesDropped)
        recorder.beginJournal(clock.tick(1000), journal::addAll)

        with(journal[0]) {
            assertEquals(4L, sequence)
            assertEquals(
                listOf("network=none", "power=low"),
                checkNotNull(checkpoint).values.map { "${it.state}=${it.value_}" },
            )
        }
        assertEquals(listOf("network:3", "power:1"), checkNotNull(journal[1].part_start).notInSession())

        // the journal lists as many of its own changes as the limit allows
        update("power", "normal")
        update("power", "low")
        update("power", "normal")
        assertEquals(listOf(5L, 6L), journal.filter { it.change != null }.map(StateRecord::sequence))
        recorder.endJournal(clock.now())
        with(checkNotNull(journal.last().part_end)) {
            assertEquals(1, dropped_changes)
            // only the state that had a change refused: the changes listed lead to the value of the other
            assertEquals(listOf("power=normal"), values.map { "${it.state}=${it.value_}" })
            assertEquals(2, global_limit)
        }
        assertEquals(1, changesDropped)
    }

    @Test
    fun `a value or an attribute longer than a span event could carry is cut to 1,997 characters and three dots`() {
        val attributes = (1..20).associate { "key-$it" to "v".repeat(5000) }
        val longKey = "k".repeat(1500)
        declare("route", attributeKeys = attributes.keys)
        declare("path", attributeKeys = setOf(longKey))
        recorder.beginJournal(clock.now(), journal::addAll)
        update("route", "r".repeat(5000), attributes)

        val cutValue = "r".repeat(1997) + "..."
        val change = checkNotNull(journal.last().change)
        assertEquals(cutValue, change.value_?.value_)
        // a declaration keeps seven keys: three fewer than an event can carry, which leaves room for the value, its type
        // and its number
        assertEquals((1..7).map { "key-$it" }, change.attributes.keys.toList())
        assertEquals(setOf("v".repeat(1997) + "..."), change.attributes.values.toSet())
        assertEquals(cutValue, recorder.snapshot().values.getValue("route").value_)

        // a value of exactly the limit is left whole
        update("route", "w".repeat(2000))
        assertEquals("w".repeat(2000), journal.last().change?.value_?.value_)

        // the cut never splits a pair: one that straddles it goes whole, in a value and in an attribute's value
        val straddling = "r".repeat(1996) + PAIR + "r".repeat(100)
        update("path", straddling, mapOf(longKey to straddling))
        val cutBeforeThePair = "r".repeat(1996) + "..."
        with(checkNotNull(journal.last().change)) {
            assertEquals(cutBeforeThePair, value_?.value_)
            // a key is cut the same way, at its own limit of 1,000
            assertEquals(mapOf("k".repeat(997) + "..." to cutBeforeThePair), this.attributes)
        }
        // a pair that ends where the cut falls is kept
        update("route", "r".repeat(1995) + PAIR + "r".repeat(100))
        assertEquals("r".repeat(1995) + PAIR + "...", journal.last().change?.value_?.value_)
    }

    @Test
    fun `a change keeps only the keys its state declared, at most seven, and never a key under either state stem`() {
        val reserved = mapOf(
            "emb.app.state.new_value" to "spoofed",
            "emb.app.state.new_value_type" to "system",
            "emb.app.state.seq" to "99",
            "emb.app.state.value.network" to "wifi",
            "emb.app.state.anything-else" to "x",
            "emb.state.anything-else" to "x",
            "emb.state.initial_value" to "v1",
            // v1's count of the transitions an instrumentation dropped, which a reporter gives beside an update's attributes
            "emb.state.dropped_by_instrumentation" to "2",
            // the stems alone, which are v1's foreground or background attribute and the SDK's own
            "emb.state" to "background",
            "emb.app.state" to "x",
        )
        val own = (1..8).associate { "key-$it" to "v$it" }
        // the declaration names the reserved keys first: they are left out of it, and use up none of the seven it keeps
        declare("network", attributeKeys = reserved.keys + own.keys)
        declare("power")
        recorder.beginJournal(clock.now(), journal::addAll)
        update("network", "wifi", reserved + own + ("undeclared" to "x"))

        // the first seven keys the declaration names that are not reserved are kept, and nothing else
        assertEquals((1..7).associate { "key-$it" to "v$it" }, checkNotNull(journal.last().change).attributes)

        // a declared key is kept wherever it comes among a change's attributes, and one that is not declared is dropped
        update("network", "wan", mapOf("undeclared" to "x", "emb.app.state.seq" to "99", "key-3" to "v"))
        assertEquals(mapOf("key-3" to "v"), checkNotNull(journal.last().change).attributes)

        // a state declared with no keys carries none
        update("power", "low", own)
        assertEquals(emptyMap<String, String>(), checkNotNull(journal.last().change).attributes)
    }

    @Test
    fun `an update for a state that no data source declared is discarded and counted, and a declared one is recorded`() {
        var undeclared = 0
        val told = mutableListOf<String>()
        val listener = object : StateChangeListener {
            override fun onChange(record: StateRecord, snapshot: StateSnapshot, previous: StateValue?) {
                told += snapshot.encoded
            }

            override fun onPartStart(timeMs: Long, snapshot: StateSnapshot) {
            }
        }
        recorder = StateRecorder(clock, onUndeclaredState = { undeclared++ }, listener = listener, keepRecent = true)

        // with no session part: nothing is held for the next part's journal
        recorder.onEvent(StateUpdate("ghost", "first", clock.tick()))
        recorder.beginJournal(clock.now(), journal::addAll)
        // and with one: no number, no value, no record, nothing told, and what it says it dropped is not counted either
        recorder.onEvent(StateUpdate("ghost", "second", clock.tick(), mapOf("key" to "value"), droppedChanges = 3))

        assertEquals(2, undeclared)
        assertEquals(0L, recorder.snapshot().sequence)
        assertTrue(recorder.snapshot().isEmpty())
        assertSame(recorder.snapshot(), recorder.snapshotAt(clock.now()))
        assertEquals(emptyList<String>(), told)
        assertEquals(emptyList<StateRecord>(), journal.filter { it.change != null })

        // the same name, once a data source declares it, is recorded from its next update on
        declare("ghost")
        declare("network")
        recorder.onEvent(StateUpdate("ghost", "third", clock.tick()))
        update("network", "wifi")
        recorder.endJournal(clock.now())

        assertEquals(2, undeclared)
        assertEquals(listOf("ghost=third", "ghost=third;network=wifi"), told)
        assertEquals(listOf(1L, 2L), journal.filter { it.change != null }.map(StateRecord::sequence))
        with(checkNotNull(journal.last().part_end)) {
            assertEquals(0, dropped_changes)
            assertEquals(emptyList<Any>(), dropped_counts)
        }
    }

    @Test
    fun `a data source declares its state on the bus before the value it starts with, and its limit and keys are applied here`() {
        val args = FakeInstrumentationArgs(
            configService = FakeConfigService(
                config = EmbraceConfig(
                    persistence = { PersistenceConfig(multiFileEnabled = { true }) },
                    autoDataCapture = { AutoDataCaptureConfig(sharedStateSpanEnabled = { true }) },
                ),
            ),
        )
        recorder = StateRecorder(clock, onUndeclaredState = { changesDropped++ })
        recorder.listenTo(args.eventBus)
        recorder.beginJournal(clock.now(), journal::addAll)
        // a data source like any other, with a limit of two and one attribute key
        val dataSource = object : StateDataSource<Any>(args, ::TestState, "UNKNOWN", 2, setOf("key")) {}
        dataSource.onDataCaptureEnabled()
        dataSource.onStateChange("a", clock.tick(), mapOf("key" to "kept", "other" to "dropped"))
        dataSource.onStateChange("b", clock.tick())
        recorder.endJournal(clock.now())

        // the value it starts with found the state declared, so it is the first change, and nothing was discarded
        assertEquals(0, changesDropped)
        assertEquals(listOf("UNKNOWN", "a"), journal.mapNotNull { it.change?.value_?.value_ })
        assertEquals(mapOf("key" to "kept"), journal.last { it.change != null }.change?.attributes)
        assertEquals("test=b", recorder.snapshot().encoded)
        with(checkNotNull(journal.last().part_end)) {
            assertEquals(listOf("test:1:2"), counts())
            assertEquals(listOf("test@3"), finalSequences())
        }
    }

    @Test
    fun `a state's limit and keys are those it was first declared with, and a second declaration of its name changes nothing`() {
        recorder.declare(StateDeclaration("network", maxChangesInPart = 1, attributeKeys = setOf("first")))
        // as a second data source that reported the same state would declare it
        recorder.declare(StateDeclaration("network", maxChangesInPart = 5, attributeKeys = setOf("second")))
        recorder.beginJournal(clock.now(), journal::addAll)
        update("network", "wifi", mapOf("first" to "kept", "second" to "dropped"))
        update("network", "wan")
        recorder.endJournal(clock.now())

        assertEquals(mapOf("first" to "kept"), journal.single { it.change != null }.change?.attributes)
        with(checkNotNull(journal.last().part_end)) {
            assertEquals(listOf("network:1:1"), counts())
        }

        // a limit below nothing lists nothing, and is stated as nothing
        recorder.declare(StateDeclaration("power", maxChangesInPart = -1))
        val nextJournal = mutableListOf<StateRecord>()
        recorder.beginJournal(clock.now(), nextJournal::addAll)
        update("power", "low")
        recorder.endJournal(clock.now())
        assertEquals(listOf("power:1:0"), checkNotNull(nextJournal.last().part_end).counts())
    }

    @Test
    fun `updates reported from several threads are journaled in the order of their sequence`() {
        recorder.beginJournal(clock.now(), journal::addAll)
        val threads = 8
        val updatesPerThread = 500
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(threads)
        repeat(threads) { thread ->
            executor.execute {
                start.await()
                // every change is to be listed, so each state is given a limit as high as the changes made to it
                repeat(updatesPerThread) { update("state-$thread", "value-$it", maxChangesInPart = updatesPerThread) }
            }
        }
        start.countDown()
        executor.shutdown()
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS))

        val changes = journal.filter { it.change != null }
        assertEquals(threads * updatesPerThread, changes.size)
        assertEquals((1L..changes.size).toList(), changes.map(StateRecord::sequence))

        // replaying the journal leads to the values the recorder holds
        val replayed = LinkedHashMap<String, String>()
        changes.forEach { record -> checkNotNull(record.change?.value_).let { replayed[it.state] = it.value_ } }
        val snapshot = recorder.snapshot()
        assertEquals(changes.size.toLong(), snapshot.sequence)
        assertEquals(replayed, snapshot.values.mapValues { it.value.value_ })
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

    /**
     * The limits a journal records as reached, each as `state:limit@sequence`, in the journal's order. The state is null
     * for the limit on the journal as a whole.
     */
    private fun List<StateRecord>.limitsReached(): List<String> = mapNotNull { record ->
        record.limit_reached?.let { "${it.state}:${it.limit}@${record.sequence}" }
    }

    /**
     * What a part start counts as made with no journal open, as `state:count` for each state it counts something for.
     */
    private fun StatePartStart.notInSession(): List<String> = not_in_session_counts.map { "${it.state}:${it.count}" }

    /**
     * What a part end counts as not listed, as `state:count:limit` for each state it counts something for.
     */
    private fun StatePartEnd.counts(): List<String> = dropped_counts.map { "${it.state}:${it.count}:${it.limit}" }

    /**
     * The number of each counted state's last change, as `state@number`, which a part end has for a state that had a
     * change refused.
     */
    private fun StatePartEnd.finalSequences(): List<String> = dropped_counts.map { "${it.state}@${it.final_sequence}" }

    private object Backgrounded : SystemStateValue {
        override fun toString(): String = "Backgrounded"
    }

    private companion object {
        const val WAIT_SECONDS = 10L

        /**
         * One character that takes a pair of code units.
         */
        const val PAIR = "\uD83D\uDE00"
    }
}
