package io.embrace.android.embracesdk.internal.arch

import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.TestStateDataSource
import io.embrace.android.embracesdk.internal.arch.datasource.UnrecordedTransitions
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class StateDataSourceTest {

    private lateinit var args: FakeInstrumentationArgs
    private lateinit var dataSource: TestStateDataSource

    @Before
    fun setUp() {
        args = FakeInstrumentationArgs(sessionPartIdSupplier = { "part-id" })
        dataSource = TestStateDataSource(args)
        dataSource.onDataCaptureEnabled()
    }

    @Test
    fun `unrecorded transitions are only reported on the part where they happened`() {
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
        repeat(MAX_TRANSITIONS + 3) {
            dataSource.onStateChange("value-$it", it.toLong())
        }
        dataSource.onPreSessionEnd()

        val token = args.destination.createdStateTokens.single()
        assertEquals(MAX_TRANSITIONS, token.transitions.size)
        assertEquals(UnrecordedTransitions(droppedByInstrumentation = 3), token.endUnrecordedTransitions)
    }

    private companion object {
        const val MAX_TRANSITIONS = 100
    }
}
