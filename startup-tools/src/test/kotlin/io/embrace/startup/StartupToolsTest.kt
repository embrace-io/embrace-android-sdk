package io.embrace.startup

import com.github.ajalt.clikt.core.parse
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase-0 smoke test: the dispatcher constructs and parses. Its real value is structural - it proves
 * the test task, the JUnit wiring and the Clikt dependency all resolve, so the golden-fixture tests
 * that arrive in phase 1 land on a working harness rather than debugging the harness itself.
 */
class StartupToolsTest {

    @Test
    fun `dispatcher parses an empty invocation`() {
        val tool = StartupTools()
        tool.parse(emptyList())
        assertEquals("startup-tools", tool.commandName)
    }

    @Test
    fun `version constant is phase-stamped`() {
        assertEquals("0.1.0-phase0", VERSION)
    }
}
