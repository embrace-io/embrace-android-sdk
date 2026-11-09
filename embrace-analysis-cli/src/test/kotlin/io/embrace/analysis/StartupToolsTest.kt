package io.embrace.analysis

import io.embrace.analysis.cli.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

internal class StartupToolsTest {

    private lateinit var out: ByteArrayOutputStream
    private lateinit var err: ByteArrayOutputStream
    private lateinit var command: RecordingCommand
    private lateinit var tools: StartupTools

    @Before
    fun setUp() {
        out = ByteArrayOutputStream()
        err = ByteArrayOutputStream()
        command = RecordingCommand()
        tools = StartupTools(listOf(command), PrintStream(out, true, Charsets.UTF_8), PrintStream(err, true, Charsets.UTF_8))
    }

    @Test
    fun `a command runs with the arguments after its name and its exit code is the tools'`() {
        assertEquals(7, tools.run(listOf("record", "a", "--b")))
        assertEquals(listOf("a", "--b"), command.args)
    }

    @Test
    fun `help lists every command with its summary and succeeds`() {
        assertEquals(0, tools.run(listOf("--help")))
        assertTrue(out.toString(Charsets.UTF_8).contains("  record   records its arguments\n"))
    }

    @Test
    fun `no command prints the usage as an error`() {
        assertEquals(2, tools.run(emptyList()))
        assertTrue(err.toString(Charsets.UTF_8).startsWith("usage: startup-tools"))
    }

    @Test
    fun `an unknown command is named in the error and nothing runs`() {
        assertEquals(2, tools.run(listOf("serve")))
        assertTrue(err.toString(Charsets.UTF_8).startsWith("startup-tools: unknown command 'serve'\nusage: startup-tools"))
        assertEquals(null, command.args)
    }

    private class RecordingCommand : Command {
        override val name = "record"
        override val summary = "records its arguments"
        var args: List<String>? = null

        override fun run(args: List<String>): Int {
            this.args = args
            return 7
        }
    }
}
