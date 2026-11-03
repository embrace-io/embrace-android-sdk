package io.embrace.android.embracesdk.internal.arch.datasource

import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.TestStateDataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

    private companion object {
        const val MAX_TRANSITIONS = 100
        const val CAPTURE_THREAD = "first-capture"
        const val PART_CHANGE_THREAD = "part-change"
        const val AWAIT_SECONDS = 5L
    }
}
