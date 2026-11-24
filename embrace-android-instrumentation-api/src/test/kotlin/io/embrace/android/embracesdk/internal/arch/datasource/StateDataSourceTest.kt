package io.embrace.android.embracesdk.internal.arch.datasource

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.TestStateDataSource
import io.embrace.android.embracesdk.internal.config.resolved.AutoDataCaptureConfig
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.config.resolved.PersistenceConfig
import io.embrace.android.embracesdk.semconv.EmbStateTransitionAttributes
import io.embrace.android.embracesdk.semconv.ExperimentalSemconv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

internal class StateDataSourceTest {

    private lateinit var args: FakeInstrumentationArgs
    private lateinit var dataSource: TestStateDataSource

    @Before
    fun setUp() {
        args = FakeInstrumentationArgs(sessionPartIdSupplier = { "part-id" })
        dataSource = TestStateDataSource(args)
    }

    @Test
    fun `unrecorded transitions are only reported on the part where they happened`() {
        dataSource.onDataCaptureEnabled()
        dataSource.onStateChange("a", 1L)
        dataSource.onStateChange("a", 2L)
        dataSource.onPreSessionEnd()
        dataSource.onPostSessionChange()
        dataSource.onStateChange("b", 3L)
        dataSource.onPreSessionEnd()

        val (first, second) = args.destination.createdStateTokens
        assertEquals(UnrecordedTransitions(droppedByInstrumentation = 1), first.endUnrecordedTransitions)
        assertEquals(listOf(UnrecordedTransitions()), second.updateUnrecordedTransitions)
        assertEquals(UnrecordedTransitions(), second.endUnrecordedTransitions)
    }

    @Test
    fun `transitions over the limit are counted as dropped`() {
        dataSource.onDataCaptureEnabled()
        repeat(MAX_TRANSITIONS + 3) {
            dataSource.onStateChange("value-$it", it.toLong())
        }
        dataSource.onPreSessionEnd()

        val token = args.destination.createdStateTokens.single()
        assertEquals(MAX_TRANSITIONS, token.transitions.size)
        assertEquals(UnrecordedTransitions(droppedByInstrumentation = 3), token.endUnrecordedTransitions)
    }

    @Test
    fun `a state span still open when another is started is ended`() {
        dataSource.onDataCaptureEnabled()
        // a part change that starts its span before the previous part's span was taken
        dataSource.onPostSessionChange()

        val tokens = args.destination.createdStateTokens
        assertEquals(2, tokens.size)
        assertNotEquals(0L, tokens[0].endTimeMs)
        assertEquals(0L, tokens[1].endTimeMs)
    }

    /**
     * The first capture of an async source runs on a worker. A part change while it is starting its span waits for it, then
     * ends that span and starts the new part's, so the span left open is the new part's.
     */
    @Test
    fun `a part change during the first capture leaves the new part's span open`() {
        val starters = Collections.synchronizedList(mutableListOf<String>())
        val capturing = CountDownLatch(1)
        val release = CountDownLatch(1)
        // recorded after any hold, so the names line up with the tokens in the order they are created
        args.destination.onStartSessionPartStateCapture = {
            if (Thread.currentThread().name == CAPTURE_THREAD) {
                capturing.countDown()
                release.await(AWAIT_SECONDS, TimeUnit.SECONDS)
            }
            starters.add(Thread.currentThread().name)
        }

        val capture = thread(name = CAPTURE_THREAD) { dataSource.onDataCaptureEnabled() }
        capturing.await(AWAIT_SECONDS, TimeUnit.SECONDS)
        val partChange = thread(name = PART_CHANGE_THREAD) {
            dataSource.onPreSessionEnd()
            dataSource.onPostSessionChange()
        }
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(AWAIT_SECONDS)
        while (partChange.isAlive && partChange.state != Thread.State.BLOCKED && System.currentTimeMillis() < deadline) {
            Thread.yield()
        }
        release.countDown()
        capture.join()
        partChange.join()

        val tokens = args.destination.createdStateTokens
        val openStarters = tokens.indices.filter { tokens[it].endTimeMs == 0L }.map { starters[it] }
        assertEquals(listOf(PART_CHANGE_THREAD), openStarters)
    }

    /**
     * A part change ends the part's span, builds the ending part's payload while that part is still the active one, then starts
     * the next part's span. A first capture in between leaves the span to the part change.
     */
    @Test
    fun `a first capture between a part's end and the next part's start leaves only the next part's span`() {
        dataSource.onPreSessionEnd()
        dataSource.onDataCaptureEnabled()
        dataSource.onPostSessionChange()

        val tokens = args.destination.createdStateTokens
        assertEquals(1, tokens.size)
        assertEquals(0L, tokens.single().endTimeMs)
    }

    @OptIn(ExperimentalSemconv::class)
    @Test
    fun `a journaled state emits each change of value and counts the transitions it did not emit`() {
        val updates = journalUpdates()
        dataSource.onDataCaptureEnabled()
        dataSource.onStateChange("a", 1L)
        dataSource.onStateChange("a", 2L)
        dataSource.onStateChange("a", 3L, droppedTransitions = 2)
        dataSource.onStateChange("b", 4L, mapOf("key" to "value"))
        dataSource.onPreSessionEnd()
        dataSource.onPostSessionChange()
        dataSource.onStateChange("c", 5L)

        assertEquals(listOf("test"), updates.map(StateUpdate::state).distinct())
        assertEquals(listOf<Any>("UNKNOWN", "a", "b", "c"), updates.map(StateUpdate::value))
        assertEquals(listOf(1L, 4L, 5L), updates.drop(1).map(StateUpdate::timeMs))
        assertEquals(emptyMap<String, String>(), updates[1].attributes)
        // two updates that changed nothing, and two transitions the instrumentation said it dropped
        assertEquals(
            mapOf("key" to "value", EmbStateTransitionAttributes.EMB_STATE_DROPPED_BY_INSTRUMENTATION to "4"),
            updates[2].attributes,
        )
        assertEquals(emptyMap<String, String>(), updates[3].attributes)
        assertEquals("c", dataSource.getCurrentStateValue())
        // a session part changing is nothing to a journaled state
        assertTrue(args.destination.createdStateTokens.isEmpty())
    }

    @Test
    fun `every update a journaled state emits is the value it holds as it is emitted`() {
        val updates = journalUpdates()
        // whatever handles an update orders it against the others there and then, so the value has to be current then
        val staleUpdates = AtomicInteger()
        args.eventBus.addHandler(StateUpdate.KEY) { update ->
            if (dataSource.getCurrentStateValue() != update.value) {
                staleUpdates.incrementAndGet()
            }
        }
        dataSource.onDataCaptureEnabled()
        val start = CountDownLatch(1)
        val reporters = (0 until REPORTING_THREADS).map { reporter ->
            thread {
                start.await(AWAIT_SECONDS, TimeUnit.SECONDS)
                repeat(UPDATES_PER_THREAD) { dataSource.onStateChange("$reporter-$it", it.toLong()) }
            }
        }
        start.countDown()
        reporters.forEach(Thread::join)

        assertEquals(REPORTING_THREADS * UPDATES_PER_THREAD + 1, updates.size)
        assertEquals(0, staleUpdates.get())
        assertEquals(dataSource.getCurrentStateValue(), updates.last().value)
    }

    /**
     * Makes [dataSource] one whose changes are journaled, and returns the updates it emits, as they are emitted.
     */
    private fun journalUpdates(): List<StateUpdate> {
        args = FakeInstrumentationArgs(
            configService = FakeConfigService(
                config = EmbraceConfig(
                    persistence = { PersistenceConfig(multiFileEnabled = { true }) },
                    autoDataCapture = { AutoDataCaptureConfig(sharedStateSpanEnabled = { true }) },
                ),
            ),
            sessionPartIdSupplier = { "part-id" },
        )
        dataSource = TestStateDataSource(args)
        val updates = Collections.synchronizedList(mutableListOf<StateUpdate>())
        args.eventBus.addHandler(StateUpdate.KEY) { updates.add(it) }
        return updates
    }

    private companion object {
        const val REPORTING_THREADS = 4
        const val UPDATES_PER_THREAD = 500
        const val MAX_TRANSITIONS = 100
        const val CAPTURE_THREAD = "first-capture"
        const val PART_CHANGE_THREAD = "part-change"
        const val AWAIT_SECONDS = 5L
    }
}
