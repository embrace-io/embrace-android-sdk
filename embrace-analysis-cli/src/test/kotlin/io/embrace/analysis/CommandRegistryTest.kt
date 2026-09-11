package io.embrace.analysis

import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.subcommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The registered subcommand list itself - [allCommands] - rather than any one command's behaviour:
 * names are unique and lower-kebab-case, the ported `check-local-refs` is present and the retired
 * `check-leaks` name is not, and every command's own `--help` renders without error.
 */
class CommandRegistryTest {

    @Test
    fun `every command name is unique`() {
        val names = allCommands().map { it.commandName }

        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `every command name is lower-kebab-case`() {
        val kebabCase = Regex("^[a-z][a-z0-9-]*$")

        allCommands().forEach { command ->
            assertTrue(command.commandName, kebabCase.matches(command.commandName))
        }
    }

    @Test
    fun `check-local-refs is registered and the retired check-leaks name is not`() {
        val names = allCommands().map { it.commandName }

        assertTrue(names.contains("check-local-refs"))
        assertFalse(names.contains("check-leaks"))
    }

    @Test
    fun `every command's help renders without error`() {
        allCommands().forEach { command ->
            val outcome = runCatching {
                StartupTools().subcommands(allCommands()).parse(listOf(command.commandName, "--help"))
            }
            val failure = outcome.exceptionOrNull()

            if (failure != null && failure !is PrintHelpMessage) {
                throw AssertionError("${command.commandName} --help failed: $failure", failure)
            }
        }
    }
}
