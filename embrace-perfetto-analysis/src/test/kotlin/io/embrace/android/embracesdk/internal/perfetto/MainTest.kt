package io.embrace.android.embracesdk.internal.perfetto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

internal class MainTest {

    @Test
    fun `a trace path is parsed, with dry run off by default`() {
        assertEquals(Options(File("a.perfetto-trace"), dryRun = false), parseArgs(arrayOf("a.perfetto-trace")))
        assertEquals(
            Options(File("a.perfetto-trace"), dryRun = true),
            parseArgs(arrayOf("a.perfetto-trace", "--dry-run")),
        )
    }

    @Test
    fun `arguments that do not name exactly one trace are rejected`() {
        listOf(
            arrayOf(),
            arrayOf("--dry-run"),
            arrayOf("a.perfetto-trace", "b.perfetto-trace"),
            arrayOf("--unknown", "a.perfetto-trace"),
        ).forEach { args ->
            assertNull(args.joinToString(" "), parseArgs(args))
        }
    }

    @Test
    fun `a dry run reports the trace and nothing else`() {
        val text = describe(Options(File("a.perfetto-trace"), dryRun = true))
        assertTrue(text, text.contains("trace: a.perfetto-trace"))
        assertTrue(text, !text.contains("not implemented"))
    }

    @Test
    fun `a real run says the analysis is not implemented`() {
        val text = describe(Options(File("a.perfetto-trace"), dryRun = false))
        assertTrue(text, text.contains("no analysis is implemented yet"))
    }
}
