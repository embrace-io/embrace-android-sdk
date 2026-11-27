package io.embrace.android.embracesdk.internal.otel.state

import org.junit.Assert.assertEquals
import org.junit.Test

internal class StateExportModeTest {

    @Test
    fun `a configured rendering wins whatever the app receives`() {
        assertEquals(StateExportMode.ASSOCIATION, StateExportMode.resolve("association", receivesLogs = false, receivesSpans = true))
        assertEquals(StateExportMode.VERBOSE, StateExportMode.resolve("verbose", receivesLogs = true, receivesSpans = false))
        assertEquals(StateExportMode.VERBOSE, StateExportMode.resolve("verbose", receivesLogs = true, receivesSpans = true))
        assertEquals(StateExportMode.ASSOCIATION, StateExportMode.resolve("association", receivesLogs = false, receivesSpans = false))
    }

    @Test
    fun `an unknown, empty or missing value falls back to what the app receives`() {
        listOf("bogus", "", "verbose mode", null).forEach { configured ->
            assertEquals(StateExportMode.ASSOCIATION, StateExportMode.resolve(configured, receivesLogs = true, receivesSpans = false))
            assertEquals(StateExportMode.VERBOSE, StateExportMode.resolve(configured, receivesLogs = false, receivesSpans = true))
            assertEquals(StateExportMode.ASSOCIATION, StateExportMode.resolve(configured, receivesLogs = false, receivesSpans = false))
            assertEquals(StateExportMode.ASSOCIATION, StateExportMode.resolve(configured, receivesLogs = true, receivesSpans = true))
        }
    }

    @Test
    fun `a configured value is matched whatever its case and surrounding space`() {
        assertEquals(StateExportMode.VERBOSE, StateExportMode.resolve(" Verbose ", receivesLogs = true, receivesSpans = true))
        assertEquals(StateExportMode.ASSOCIATION, StateExportMode.resolve("ASSOCIATION", receivesLogs = false, receivesSpans = true))
    }
}
