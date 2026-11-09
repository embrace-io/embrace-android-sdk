package io.embrace.analysis.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import java.nio.file.Paths

internal class TraceProcessorCommandTest {

    private lateinit var out: ByteArrayOutputStream
    private lateinit var err: ByteArrayOutputStream

    @Before
    fun setUp() {
        out = ByteArrayOutputStream()
        err = ByteArrayOutputStream()
    }

    @Test
    fun `the installed binary's path is printed`() {
        assertEquals(0, command { Paths.get("/cache/v1/mac-arm64/trace_processor_shell") }.run(emptyList()))
        assertEquals("/cache/v1/mac-arm64/trace_processor_shell\n", out.toString(Charsets.UTF_8))
    }

    @Test
    fun `a binary that can't be installed is reported`() {
        assertEquals(1, command { error("download failed") }.run(emptyList()))
        assertEquals("trace-processor: download failed\n", err.toString(Charsets.UTF_8))
    }

    @Test
    fun `arguments are a usage error, help is not`() {
        assertEquals(2, command { error("not installed") }.run(listOf("extra")))
        assertTrue(err.toString(Charsets.UTF_8).startsWith("trace-processor: takes no arguments\nusage: startup-tools trace-processor"))
        assertEquals(0, command { error("not installed") }.run(listOf("--help")))
        assertTrue(out.toString(Charsets.UTF_8).startsWith("usage: startup-tools trace-processor"))
    }

    private fun command(install: () -> Path) = TraceProcessorCommand(
        out = PrintStream(out, true, Charsets.UTF_8),
        err = PrintStream(err, true, Charsets.UTF_8),
        installTraceProcessor = install,
    )
}
