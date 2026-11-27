package io.embrace.android.embracesdk.internal.arch.datasource

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.TestState
import io.embrace.android.embracesdk.fakes.TestStateDataSource
import io.embrace.android.embracesdk.internal.config.resolved.AutoDataCaptureConfig
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.config.resolved.PersistenceConfig
import io.embrace.android.embracesdk.semconv.EmbStateTransitionAttributes
import io.embrace.android.embracesdk.semconv.ExperimentalSemconv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /**
     * The declarations a journaled data source emitted, and everything it emitted, in order: `declared <state>` for a
     * declaration and the value for an update.
     */
    private lateinit var declarations: MutableList<StateDeclaration>
    private lateinit var emitted: MutableList<String>

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
    fun `a journaled state declares itself once before its first update, and emits each change of value and the dropped transitions`() {
        val updates = journalUpdates()
        dataSource.onDataCaptureEnabled()
        dataSource.onStateChange("a", 1L)
        // a repeated value loses nothing, so it is neither emitted nor counted
        dataSource.onStateChange("a", 2L)
        // one that comes with a count is emitted, so that the count is reported, and carries nothing else
        dataSource.onStateChange("a", 3L, mapOf("ignored" to "value"), droppedTransitions = 2)
        dataSource.onStateChange("b", 4L, mapOf("key" to "value"))
        dataSource.onPreSessionEnd()
        dataSource.onPostSessionChange()
        dataSource.onStateChange("c", 5L, droppedTransitions = 1)

        // the state is declared once, as capture starts: its name, the limit every state has unless its data source gives
        // another, and no attribute keys
        with(declarations.single()) {
            assertEquals("test", state)
            assertEquals(MAX_TRANSITIONS, maxChangesInPart)
            assertEquals(emptySet<String>(), attributeKeys)
        }
        // and before anything is reported of it, the default value that capture starts with included
        assertEquals(listOf("declared test", "UNKNOWN", "a"), emitted.take(3))

        assertEquals(listOf("test"), updates.map(StateUpdate::state).distinct())
        assertEquals(listOf<Any>("UNKNOWN", "a", "a", "b", "c"), updates.map(StateUpdate::value))
        assertEquals(listOf(1L, 3L, 4L, 5L), updates.drop(1).map(StateUpdate::timeMs))
        assertEquals(listOf(0, 0, 2, 0, 1), updates.map(StateUpdate::droppedChanges))
        // an update carries every attribute it was reported with: which are kept is for whatever records the updates
        assertEquals(
            listOf(emptyMap(), emptyMap(), emptyMap(), mapOf("key" to "value"), emptyMap()),
            updates.map(StateUpdate::attributes),
        )
        // the count is not written as an attribute of a change
        assertTrue(updates.none { EmbStateTransitionAttributes.EMB_STATE_DROPPED_BY_INSTRUMENTATION in it.attributes })
        assertEquals("c", dataSource.getCurrentStateValue())
        // a session part changing is nothing to a journaled state
        assertTrue(args.destination.createdStateTokens.isEmpty())

        // a data source that gives a limit and attribute keys of its own declares those, and applies neither itself
        val limited = object : StateDataSource<Any>(args, ::TestState, "UNKNOWN", 2, setOf("key")) {}
        limited.onDataCaptureEnabled()
        repeat(5) { limited.onStateChange("value-$it", it.toLong(), mapOf("key" to "kept", "other" to "passed on")) }
        with(declarations.last()) {
            assertEquals(2, maxChangesInPart)
            assertEquals(setOf("key"), attributeKeys)
        }
        assertEquals(listOf<Any>("UNKNOWN", "value-0", "value-1", "value-2", "value-3", "value-4"), updates.drop(5).map(StateUpdate::value))
        assertEquals(setOf("key", "other"), updates.last().attributes.keys)

        // a data source whose first value arrives before its capture is enabled declares itself before that value, and once
        val early = TestStateDataSource(args)
        val emittedBefore = emitted.size
        early.onStateChange("first", 1L)
        early.onDataCaptureEnabled()
        early.onStateChange("second", 2L)
        assertEquals(listOf("declared test", "first", "second"), emitted.drop(emittedBefore))
    }

    @Test
    fun `a state captured lazily is present from the start when journaled, and starts on first use when it has a span of its own`() {
        val updates = journalUpdates()
        val journaled = lazyDataSource()
        journaled.onDataCaptureEnabled()

        // its default is reported as capture is enabled, whatever the data source says about capturing on creation
        assertEquals(listOf<Any>("UNKNOWN"), updates.map(StateUpdate::value))
        assertTrue(journaled.isActive())

        // with a span for each state, nothing is captured until the state first changes
        args = FakeInstrumentationArgs(sessionPartIdSupplier = { "part-id" })
        val onTheBus = mutableListOf<Any>()
        args.eventBus.addHandler(StateDeclaration.KEY) { onTheBus.add(it) }
        args.eventBus.addHandler(StateUpdate.KEY) { onTheBus.add(it) }
        val withSpan = lazyDataSource()
        withSpan.onDataCaptureEnabled()
        assertFalse(withSpan.isActive())
        assertTrue(args.destination.createdStateTokens.isEmpty())
        withSpan.onStateChange("a", 1L)
        assertTrue(withSpan.isActive())
        assertEquals(1, args.destination.createdStateTokens.size)
        // and it declares nothing and reports nothing on the event bus
        assertEquals(emptyList<Any>(), onTheBus)
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
     * A data source of the test state that does not capture on creation, as the power state's does not.
     */
    private fun lazyDataSource(): StateDataSource<Any> = object : StateDataSource<Any>(args, ::TestState, "UNKNOWN") {
        override val captureStateOnCreation = false
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
        declarations = Collections.synchronizedList(mutableListOf())
        emitted = Collections.synchronizedList(mutableListOf())
        args.eventBus.addHandler(StateDeclaration.KEY) { declaration ->
            declarations.add(declaration)
            emitted.add("declared ${declaration.state}")
        }
        args.eventBus.addHandler(StateUpdate.KEY) { update ->
            updates.add(update)
            emitted.add(update.value.toString())
        }
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
