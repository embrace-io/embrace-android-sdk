package io.embrace.android.embracesdk.internal.capture.state

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.arch.datasource.StateUpdate
import io.embrace.android.embracesdk.internal.arch.schema.SystemStateValue
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.session.persistence.StatePartEnd
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
    fun `a listener is told of each recorded change as it is made, with the values after it, and of each part that starts`() {
        val told = mutableListOf<String>()
        val listener = object : StateChangeListener {
            override fun onChange(record: StateRecord, snapshot: StateSnapshot, previous: StateValue?) {
                assertEquals(record.sequence, snapshot.sequence)
                told.add("${record.sequence}:${snapshot.encoded}")
            }

            override fun onPartStart(timeMs: Long, snapshot: StateSnapshot) {
                told.add("part@$timeMs:${snapshot.sequence}")
            }
        }
        recorder = StateRecorder(clock, maxChanges = 3, maxHeldChanges = 1, listener = listener)

        // with no part in progress a change is reported when it is made, even one that is later folded away
        update("network", "wifi")
        update("network", "wan")
        assertEquals(listOf("1:network=wifi", "2:network=wan"), told)

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
                "1:network=wifi",
                "2:network=wan",
                "part@$partStart:2",
                "3:network=wan;power=low",
                "4:network=none;power=low",
            ),
            told,
        )
        assertEquals(6L, recorder.snapshot().sequence)
    }

    @Test
    fun `changes made with no session part in progress open the journal of the next part`() {
        val processStart = clock.now()
        update("network", "wifi")
        update("network", "wan")
        assertTrue(journal.isEmpty())

        val partStart = clock.tick(1000)
        recorder.beginJournal(partStart, journal::addAll)

        // every state had no value when the process started
        with(journal[0]) {
            assertEquals(0L, sequence)
            assertEquals(processStart, time_ms)
            assertEquals(emptyList<Any>(), checkNotNull(checkpoint).values)
        }
        assertEquals(listOf("wifi", "wan"), journal.subList(1, 3).map { it.change?.value_?.value_ })
        with(journal[3]) {
            assertNotNull(part_start)
            assertEquals(2L, sequence)
            assertEquals(partStart, time_ms)
        }

        update("network", "none")
        val partEnd = clock.tick(1000)
        recorder.endJournal(partEnd)
        with(journal.last()) {
            assertNotNull(part_end)
            assertEquals(3L, sequence)
            assertEquals(partEnd, time_ms)
            assertEquals(0, checkNotNull(part_end).dropped_changes)
        }

        // the next journal opens with the values the last one ended with
        update("power", "low")
        val nextJournal = mutableListOf<StateRecord>()
        recorder.beginJournal(clock.tick(1000), nextJournal::addAll)
        with(nextJournal[0]) {
            assertEquals(3L, sequence)
            assertEquals(partEnd, time_ms)
            assertEquals(listOf("network=none"), checkNotNull(checkpoint).values.map { "${it.state}=${it.value_}" })
        }
        assertEquals("low", nextJournal[1].change?.value_?.value_)
        assertEquals(4L, nextJournal[2].sequence)
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
        }

        assertEquals(2, changesDropped)
        assertEquals(emptyList<String>(), stateLimitsApplied)

        // each journal has the limit to itself
        val nextJournal = mutableListOf<StateRecord>()
        recorder.beginJournal(clock.now(), nextJournal::addAll)
        update("power", "normal")
        assertEquals(1, nextJournal.count { it.change != null })
        assertEquals(0, checkNotNull(nextJournal.single { it.part_start != null }.part_start).dropped_changes)
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
        // changes held from before the part are listed, and use up none of the state's limit for the part
        repeat(3) { update("network", "held-$it", maxChangesInPart = 2) }
        recorder.beginJournal(clock.now(), journal::addAll)
        update("network", "wifi", maxChangesInPart = 2)
        update("network", "wan", maxChangesInPart = 2)
        val firstRefusedAt = update("network", "none", maxChangesInPart = 2)
        update("power", "low")
        update("network", "wifi", maxChangesInPart = 2)
        update("power", "normal")

        // the refused changes keep their numbers, so the numbers of the changes listed skip in the middle
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 7L, 9L), journal.filter { it.change != null }.map(StateRecord::sequence))
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 7L, 9L), told)
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
    fun `a state whose reporter names no limit lists 100 changes in a session part`() {
        recorder.beginJournal(clock.now(), journal::addAll)
        repeat(StateUpdate.DEFAULT_MAX_CHANGES_IN_PART + 1) { update("network", "value-$it") }
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
            // the changes listed still lead to the last value reported, so none is given
            assertEquals(emptyList<Any>(), values)
        }

        // reported with no part in progress, it is counted in the next part's journal, and in no part after that
        update("power", "low", droppedChanges = 4)
        val nextJournal = mutableListOf<StateRecord>()
        recorder.beginJournal(clock.now(), nextJournal::addAll)
        update("power", "normal", droppedChanges = 1)
        recorder.endJournal(clock.now())
        assertEquals(listOf("power:5:null"), checkNotNull(nextJournal.last().part_end).counts())

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
    fun `more changes than are held for the next journal are folded into the values it opens with`() {
        recorder = StateRecorder(clock, maxChanges = 4, maxHeldChanges = 2, onChangeDropped = { changesDropped++ })
        update("network", "wifi")
        val lastFolded = update("power", "low")
        update("network", "wan")
        update("network", "none")
        recorder.beginJournal(clock.tick(1000), journal::addAll)

        // the two oldest changes are no longer records, and the journal opens with the values after them
        with(journal[0]) {
            assertEquals(2L, sequence)
            assertEquals(lastFolded, time_ms)
            assertEquals(
                listOf("network=wifi", "power=low"),
                checkNotNull(checkpoint).values.map { "${it.state}=${it.value_}" },
            )
        }
        assertEquals(listOf(3L, 4L), journal.subList(1, 3).map(StateRecord::sequence))
        assertEquals(listOf("wan", "none"), journal.subList(1, 3).map { it.change?.value_?.value_ })
        with(journal[3]) {
            assertEquals(4L, sequence)
            assertEquals(2, checkNotNull(part_start).dropped_changes)
        }
        assertEquals(2, changesDropped)

        // the changes a journal opens with count towards its limit
        update("power", "normal")
        update("power", "low")
        update("power", "normal")
        assertEquals(listOf(3L, 4L, 5L, 6L), journal.filter { it.change != null }.map(StateRecord::sequence))
        recorder.endJournal(clock.now())
        with(checkNotNull(journal.last().part_end)) {
            assertEquals(1, dropped_changes)
            // only the state that had a change refused: the changes listed lead to the value of the other
            assertEquals(listOf("power=normal"), values.map { "${it.state}=${it.value_}" })
            assertEquals(4, global_limit)
        }

        // nothing was folded before the next journal
        val nextJournal = mutableListOf<StateRecord>()
        recorder.beginJournal(clock.now(), nextJournal::addAll)
        assertEquals(7L, nextJournal[0].sequence)
        assertEquals(0, checkNotNull(nextJournal[1].part_start).dropped_changes)
    }

    @Test
    fun `a value or an attribute longer than a span event could carry is cut to 1,997 characters and three dots`() {
        recorder.beginJournal(clock.now(), journal::addAll)
        val attributes = (1..20).associate { "key-$it" to "v".repeat(5000) }
        update("route", "r".repeat(5000), attributes)

        val cutValue = "r".repeat(1997) + "..."
        val change = checkNotNull(journal.last().change)
        assertEquals(cutValue, change.value_?.value_)
        // three fewer than an event can carry, which leaves room for the value, its type and its number
        assertEquals(7, change.attributes.size)
        assertEquals(setOf("v".repeat(1997) + "..."), change.attributes.values.toSet())
        assertEquals(cutValue, recorder.snapshot().values.getValue("route").value_)

        // a value of exactly the limit is left whole
        update("route", "w".repeat(2000))
        assertEquals("w".repeat(2000), journal.last().change?.value_?.value_)

        // the cut never splits a pair: one that straddles it goes whole, in a value and in an attribute's value
        val straddling = "r".repeat(1996) + PAIR + "r".repeat(100)
        update("route", straddling, mapOf("k".repeat(1500) to straddling))
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
    fun `a change's own attribute under either state stem is dropped, and uses up none of the seven a change can carry`() {
        recorder.beginJournal(clock.now(), journal::addAll)
        val reserved = mapOf(
            "emb.app.state.new_value" to "spoofed",
            "emb.app.state.new_value_type" to "system",
            "emb.app.state.seq" to "99",
            "emb.app.state.value.network" to "wifi",
            "emb.app.state.anything-else" to "x",
            "emb.state.anything-else" to "x",
            "emb.state.initial_value" to "v1",
            // the stems alone, which are v1's foreground or background attribute and the SDK's own
            "emb.state" to "background",
            "emb.app.state" to "x",
        )
        val own = (1..8).associate { "key-$it" to "v$it" }
        update("network", "wifi", reserved + own)

        // the reserved keys are dropped first and the first seven of the rest are kept, in the order they were given
        assertEquals((1..7).associate { "key-$it" to "v$it" }, checkNotNull(journal.last().change).attributes)

        // v1's count of the transitions an instrumentation dropped is reserved like any other key under its stem: a
        // reporter gives that count beside the update's attributes, and not among them
        update("network", "wan", mapOf("emb.state.dropped_by_instrumentation" to "2", "emb.app.state.seq" to "99", "key" to "v"))
        assertEquals(mapOf("key" to "v"), checkNotNull(journal.last().change).attributes)
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

    private fun update(
        state: String,
        value: Any,
        attributes: Map<String, String> = emptyMap(),
        maxChangesInPart: Int = StateUpdate.DEFAULT_MAX_CHANGES_IN_PART,
        droppedChanges: Int = 0,
    ): Long {
        val time = clock.tick()
        recorder.onEvent(StateUpdate(state, value, time, attributes, maxChangesInPart, droppedChanges))
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
     * What a part end counts as not listed, as `state:count:limit` for each state it counts something for.
     */
    private fun StatePartEnd.counts(): List<String> = dropped_counts.map { "${it.state}:${it.count}:${it.limit}" }

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
