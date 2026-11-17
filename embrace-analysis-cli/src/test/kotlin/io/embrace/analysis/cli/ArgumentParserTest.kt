package io.embrace.analysis.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

internal class ArgumentParserTest {

    private val parser =
        ArgumentParser(flags = setOf("--all"), valued = setOf("--out", "--arm"), repeatable = setOf("--arm"))

    @Test
    fun `flags, valued options in either form and positionals are read, repeatable values in order`() {
        val parsed = parser.parse(listOf("a", "--all", "--out", "x", "--arm=b1", "b", "--arm", "b2"))
        assertEquals(
            ParsedArguments(
                flags = setOf("--all"),
                values = mapOf("--out" to listOf("x"), "--arm" to listOf("b1", "b2")),
                positional = listOf("a", "b"),
            ),
            parsed,
        )
    }

    @Test
    fun `a value after the equals sign may itself hold one, and may be empty`() {
        assertEquals(mapOf("--out" to listOf("a=b")), parser.parse(listOf("--out=a=b"))?.values)
        assertEquals(mapOf("--out" to listOf("")), parser.parse(listOf("--out="))?.values)
    }

    @Test
    fun `help anywhere asks for the help text, and nothing at all is no arguments`() {
        assertNull(parser.parse(listOf("a", "--help")))
        assertNull(parser.parse(listOf("-h", "--bogus")))
        assertEquals(ParsedArguments(emptySet(), emptyMap(), emptyList()), parser.parse(emptyList()))
    }

    @Test
    fun `an unknown option, a missing value and a repeat of an option that isn't repeatable are refused by name`() {
        assertUsage("unknown option --bogus", "--bogus")
        assertUsage("unknown option -x", "-x")
        assertUsage("--out needs a value", "--out")
        assertUsage("--out given more than once", "--out", "a", "--out", "b")
    }

    private fun assertUsage(message: String, vararg args: String) {
        val e = assertThrows(UsageException::class.java) { parser.parse(args.toList()) }
        assertEquals(message, e.message)
    }
}
