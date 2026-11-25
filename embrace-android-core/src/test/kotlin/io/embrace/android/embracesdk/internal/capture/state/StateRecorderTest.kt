package io.embrace.android.embracesdk.internal.capture.state

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.arch.datasource.StateUpdate
import io.embrace.android.embracesdk.internal.arch.schema.SystemStateValue
import io.embrace.android.embracesdk.internal.clock.Clock
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

    @Before
    fun setUp() {
        clock = FakeClock()
        recorder = StateRecorder(clock)
        journal = mutableListOf()
        changesDropped = 0
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
                "emb.states.value.screen" to "Backgrounded",
                "emb.states.value_type.screen" to "system",
                "emb.states.value.network" to "none",
                "emb.states.value.power" to "low",
            ),
            snapshot.attributes,
        )
        // built once for a snapshot, so that everything stamped until the next change shares it
        assertTrue(snapshot.attributes === snapshot.attributes)
        assertEquals(
            mapOf(
                "emb.states.value.screen" to "Backgrounded",
                "emb.states.value_type.screen" to "system",
                "emb.states.value.network" to "wifi",
            ),
            earlier.attributes,
        )

        // a state that has its first value differs from having none
        assertEquals(
            mapOf("emb.states.end_value.network" to "none", "emb.states.end_value.power" to "low"),
            snapshot.endAttributes(earlier),
        )
        assertEquals(
            mapOf(
                "emb.states.end_value.screen" to "Backgrounded",
                "emb.states.end_value_type.screen" to "system",
                "emb.states.end_value.network" to "none",
                "emb.states.end_value.power" to "low",
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
        recorder = StateRecorder(clock, maxChanges = 2, onChangeDropped = { changesDropped++ })
        recorder.beginJournal(clock.now(), journal::addAll)
        update("network", "wifi")
        update("network", "wan")
        update("network", "none")
        update("power", "low")

        assertEquals(2, journal.count { it.change != null })
        assertEquals("network=none;power=low", recorder.snapshot().encoded)
        assertEquals(4L, recorder.snapshot().sequence)

        recorder.endJournal(clock.now())
        with(checkNotNull(journal.last().part_end)) {
            assertEquals(2, dropped_changes)
            assertEquals(listOf("network=none", "power=low"), values.map { "${it.state}=${it.value_}" })
        }

        assertEquals(2, changesDropped)

        // each journal has the limit to itself
        val nextJournal = mutableListOf<StateRecord>()
        recorder.beginJournal(clock.now(), nextJournal::addAll)
        update("power", "normal")
        assertEquals(1, nextJournal.count { it.change != null })
        assertEquals(0, checkNotNull(nextJournal.single { it.part_start != null }.part_start).dropped_changes)
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
            assertEquals(listOf("network=none", "power=normal"), values.map { "${it.state}=${it.value_}" })
        }

        // nothing was folded before the next journal
        val nextJournal = mutableListOf<StateRecord>()
        recorder.beginJournal(clock.now(), nextJournal::addAll)
        assertEquals(7L, nextJournal[0].sequence)
        assertEquals(0, checkNotNull(nextJournal[1].part_start).dropped_changes)
    }

    @Test
    fun `a value or an attribute longer than a span event could carry is cut short`() {
        recorder.beginJournal(clock.now(), journal::addAll)
        val attributes = (1..20).associate { "key-$it" to "v".repeat(5000) }
        update("route", "r".repeat(5000), attributes)

        val change = checkNotNull(journal.last().change)
        assertEquals(2000, change.value_?.value_?.length)
        // three fewer than an event can carry, which leaves room for the value, its type and its number
        assertEquals(7, change.attributes.size)
        assertEquals(setOf(2000), change.attributes.values.map(String::length).toSet())
        assertEquals(2000, recorder.snapshot().values.getValue("route").value_.length)
    }

    @Test
    fun `a change's own attribute under either state stem is dropped, and uses up none of the seven a change can carry`() {
        recorder.beginJournal(clock.now(), journal::addAll)
        val reserved = mapOf(
            "emb.states.new_value" to "spoofed",
            "emb.states.new_value_type" to "system",
            "emb.states.seq" to "99",
            "emb.states.value.network" to "wifi",
            "emb.states.anything-else" to "x",
            "emb.state.anything-else" to "x",
            "emb.state.initial_value" to "v1",
            // the stems alone, which are v1's foreground or background attribute and the SDK's own
            "emb.state" to "background",
            "emb.states" to "x",
        )
        val own = (1..8).associate { "key-$it" to "v$it" }
        update("network", "wifi", reserved + own)

        // the reserved keys are dropped first and the first seven of the rest are kept, in the order they were given
        assertEquals((1..7).associate { "key-$it" to "v$it" }, checkNotNull(journal.last().change).attributes)

        // the count of transitions an instrumentation dropped is the one key under the prefix that a data source reports
        update("network", "wan", mapOf("emb.state.dropped_by_instrumentation" to "2", "emb.states.seq" to "99"))
        assertEquals(mapOf("emb.state.dropped_by_instrumentation" to "2"), checkNotNull(journal.last().change).attributes)
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
                repeat(updatesPerThread) { update("state-$thread", "value-$it") }
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

    private fun update(state: String, value: Any, attributes: Map<String, String> = emptyMap()): Long {
        val time = clock.tick()
        recorder.onEvent(StateUpdate(state, value, time, attributes))
        return time
    }

    private object Backgrounded : SystemStateValue {
        override fun toString(): String = "Backgrounded"
    }

    private companion object {
        const val WAIT_SECONDS = 10L
    }
}
