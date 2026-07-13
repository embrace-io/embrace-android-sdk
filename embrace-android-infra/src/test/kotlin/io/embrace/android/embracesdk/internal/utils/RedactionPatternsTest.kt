package io.embrace.android.embracesdk.internal.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.regex.Pattern

internal class RedactionPatternsTest {

    @Test
    fun `value is unchanged when no pattern matches`() {
        val rules = RedactionPatterns(
            patterns = listOf(Pattern.compile("https://example\\.com/users/(\\d+)")),
        )
        assertEquals("https://example.com/orders/123", rules.redacted("https://example.com/orders/123"))
    }

    @Test
    fun `single capture group is redacted`() {
        val rules = RedactionPatterns(
            patterns = listOf(Pattern.compile("https://example\\.com/users/(\\d+)")),
        )
        assertEquals(
            "https://example.com/users/<redacted>",
            rules.redacted("https://example.com/users/1234"),
        )
    }

    @Test
    fun `multiple capture groups are all redacted`() {
        val rules = RedactionPatterns(
            patterns = listOf(Pattern.compile("https://example\\.com/users/(\\d+)/orders/(\\d+)")),
        )
        assertEquals(
            "https://example.com/users/<redacted>/orders/<redacted>",
            rules.redacted("https://example.com/users/1234/orders/5678"),
        )
    }

    @Test
    fun `text outside capture groups is preserved`() {
        val rules = RedactionPatterns(
            patterns = listOf(Pattern.compile("(secret)-suffix")),
        )
        assertEquals("<redacted>-suffix", rules.redacted("secret-suffix"))
    }

    @Test
    fun `pattern with no capture groups redacts the whole value`() {
        val rules = RedactionPatterns(
            patterns = listOf(Pattern.compile("https://example\\.com/secret")),
        )
        assertEquals("<redacted>", rules.redacted("https://example.com/secret"))
    }

    @Test
    fun `first matching pattern wins and later patterns are not applied`() {
        val rules = RedactionPatterns(
            patterns = listOf(
                Pattern.compile("https://example\\.com/users/(\\d+)"),
                Pattern.compile("https://example\\.com/(.*)"),
            ),
        )
        assertEquals(
            "https://example.com/users/<redacted>",
            rules.redacted("https://example.com/users/1234"),
        )
    }

    @Test
    fun `custom redaction label is used`() {
        val rules = RedactionPatterns(
            patterns = listOf(Pattern.compile("https://example\\.com/users/(\\d+)")),
            redactionLabel = "<hidden>",
        )
        assertEquals(
            "https://example.com/users/<hidden>",
            rules.redacted("https://example.com/users/1234"),
        )
    }

    @Test
    fun `non-participating optional group is skipped`() {
        val rules = RedactionPatterns(
            patterns = listOf(Pattern.compile("https://example\\.com/users/(\\d+)(/orders/(\\d+))?")),
        )
        assertEquals(
            "https://example.com/users/<redacted>",
            rules.redacted("https://example.com/users/1234"),
        )
    }

    @Test
    fun `empty pattern list returns value unchanged`() {
        val rules = RedactionPatterns(patterns = emptyList())
        assertEquals("https://example.com/users/1234", rules.redacted("https://example.com/users/1234"))
    }
}
