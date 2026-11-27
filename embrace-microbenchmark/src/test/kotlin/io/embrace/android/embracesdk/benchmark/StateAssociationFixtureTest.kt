package io.embrace.android.embracesdk.benchmark

import io.embrace.android.embracesdk.benchmark.StateAssociationFixture.Exported
import io.embrace.android.embracesdk.internal.state.AppStateKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A benchmark of [StateAssociationFixture] measures nothing if its mode does not do what it is named for, so each mode is
 * checked here against what the app's exporters and Embrace's payload are handed. An app's exporters are run on the SDK's
 * export thread, so what they were handed is read once the last of it has arrived.
 */
internal class StateAssociationFixtureTest {

    private val states = 3

    @Test
    fun `with the model off nothing is linked, stamped or replayed`() {
        StateAssociationFixture(0, StateAssociationMode.NONE).use { fixture ->
            fixture.startAndEndSpan()
            fixture.recordLog()
            val exported = fixture.exportedWhen { it.spans == 1 && it.logs == 1 }
            assertTrue(fixture.storedLogAttributes().single().keys.none { it.startsWith(AppStateKeys.STEM) })
            assertEquals(0, exported.spansLinked)
            assertEquals(0, exported.logsStamped)
            assertEquals(0, exported.stateEvents)
        }
    }

    @Test
    fun `the first step stamps a log, replays every change and links no span`() {
        StateAssociationFixture(states, StateAssociationMode.FIRST_STEP).use { fixture ->
            fixture.startAndEndSpan()
            fixture.recordLog()
            // the part's snapshot and each state's first change
            val exported = fixture.exportedWhen { it.spans == 1 && it.logs == 1 && it.stateEvents >= 1 + states }
            val stored = fixture.storedLogAttributes().single()
            assertTrue(stored.containsKey(AppStateKeys.PACKED))
            assertTrue(stored.containsKey(AppStateKeys.SEQ))
            assertEquals(states, fixture.stampLog().keys.count { it.startsWith(AppStateKeys.VALUE_PREFIX) })
            assertTrue(fixture.stampLogPacked().isNotEmpty())
            assertEquals(0, exported.spansLinked)
            assertEquals(1, exported.logsStamped)
            assertEquals(1 + states, exported.stateEvents)
        }
    }

    @Test
    fun `without a log exporter a span carries the values and nothing is replayed`() {
        StateAssociationFixture(states, StateAssociationMode.NO_APP_LOG_EXPORTER).use { fixture ->
            fixture.startAndEndSpan()
            fixture.recordLog()
            val exported = fixture.exportedWhen { it.spans == 1 }
            assertTrue(fixture.storedLogAttributes().single().containsKey(AppStateKeys.PACKED))
            assertEquals(1, exported.spansLinked)
            assertEquals(1, exported.spansWithValues)
            assertEquals(0, exported.logs)
            assertEquals(0, exported.stateEvents)
        }
    }

    @Test
    fun `the association rendering links a span with the numbers alone and replays every change`() {
        val exported = replayed(StateAssociationMode.ASSOCIATION)
        assertEquals(1, exported.spansLinked)
        assertEquals(0, exported.spansWithValues)
        assertEquals(1, exported.logsStamped)
        assertEquals(EVENTS_REPLAYED, exported.stateEvents)
    }

    @Test
    fun `the verbose rendering also puts the values on a span`() {
        val exported = replayed(StateAssociationMode.VERBOSE)
        assertEquals(1, exported.spansLinked)
        assertEquals(1, exported.spansWithValues)
        assertEquals(1, exported.logsStamped)
        assertEquals(EVENTS_REPLAYED, exported.stateEvents)
    }

    @Test
    fun `changes keep being replayed in every session part the fixture starts`() {
        StateAssociationFixture(1, StateAssociationMode.ASSOCIATION).use { fixture ->
            repeat(PARTS) {
                fixture.startPart()
                repeat(CHANGES) { fixture.transition(0) }
            }
            // the first part's snapshot and the state's first change, then a snapshot and the changes of each part
            val expected = 2 + PARTS * (1 + CHANGES)
            assertEquals(expected, fixture.exportedWhen { it.stateEvents >= expected }.stateEvents)
        }
    }

    private fun replayed(mode: StateAssociationMode): Exported =
        StateAssociationFixture(states, mode).use { fixture ->
            repeat(CHANGES) { fixture.transition(it % states) }
            fixture.startAndEndSpan()
            fixture.recordLog()
            fixture.exportedWhen { it.spans == 1 && it.logs == 1 && it.stateEvents >= EVENTS_REPLAYED }
        }

    private fun StateAssociationFixture.exportedWhen(condition: (Exported) -> Boolean): Exported {
        awaitEvents()
        val deadline = System.nanoTime() + EXPORT_TIMEOUT_NANOS
        var exported = exported()
        while (!condition(exported) && System.nanoTime() < deadline) {
            Thread.sleep(POLL_MS)
            exported = exported()
        }
        return exported
    }

    private companion object {
        const val CHANGES = 4
        const val PARTS = 3
        const val POLL_MS = 5L
        const val EXPORT_TIMEOUT_NANOS = 10_000_000_000L

        // the part's snapshot, each of the three states' first change, and each of the changes the test makes
        const val EVENTS_REPLAYED = 1 + 3 + CHANGES
    }
}
