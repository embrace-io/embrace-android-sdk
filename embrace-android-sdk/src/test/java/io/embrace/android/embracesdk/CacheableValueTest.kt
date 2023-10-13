package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.internal.CacheableValue
import org.junit.Assert.assertEquals
import org.junit.Test

internal class CacheableValueTest {

    @Test
    fun testCaching() {
        var value = "test"
        val cache = CacheableValue<String> { value }
        assertEquals("test", cache.value { "test" })
        assertEquals("test", cache.value { throw IllegalStateException() })
        assertEquals("test", cache.value { throw IllegalStateException() })

        value = "foo"
        assertEquals("another", cache.value { "another" })
    }

    @Test
    fun testHashcode() {
        var value = -1
        val cache = CacheableValue<Int> { value }
        assertEquals(5, cache.value { 5 })
        assertEquals(5, cache.value { throw IllegalStateException() })
        assertEquals(5, cache.value { throw IllegalStateException() })

        value = 79
        assertEquals(22, cache.value { 22 })
    }

    @Test(expected = IllegalStateException::class)
    fun testNullNotSupported() {
        val cache = CacheableValue<String?> { "test" }
        assertEquals("test", cache.value { null })
    }
}
