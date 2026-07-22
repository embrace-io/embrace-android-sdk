package io.embrace.android.embracesdk.internal.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class PatternCacheTest {

    private val cache = PatternCache()

    @Test
    fun testFullStringMatch() {
        assertTrue(cache.doesStringMatchPatternInSet("example.com", setOf("example.com")))
        assertFalse(cache.doesStringMatchPatternInSet("https://example.com/path", setOf("example.com")))
    }

    @Test
    fun testSubstringMatch() {
        assertTrue(cache.doesStringContainMatchInSet("https://example.com/path", setOf("example.com")))
        assertFalse(cache.doesStringContainMatchInSet("https://google.com/path", setOf("example.com")))
    }

    @Test
    fun testInvalidPatternsIgnored() {
        assertFalse(cache.doesStringMatchPatternInSet("invalid[}regex", setOf("invalid[}regex")))
        assertTrue(cache.doesStringContainMatchInSet("example.com", setOf("invalid[}regex", "example.com")))
    }

    @Test
    fun testListsWithDuplicatesSupported() {
        assertTrue(cache.doesStringContainMatchInSet("https://example.com/path", listOf("example.com", "example.com")))
        assertFalse(cache.doesStringContainMatchInSet("https://google.com/path", listOf("example.com", "example.com")))
    }

    @Test
    fun testMatchingWorksBeyondCacheCap() {
        repeat(50) { k ->
            assertTrue(cache.doesStringContainMatchInSet("url$k", setOf("url$k", "other$k")))
            assertFalse(cache.doesStringContainMatchInSet("nomatch", setOf("url$k", "other$k")))
        }
    }
}
