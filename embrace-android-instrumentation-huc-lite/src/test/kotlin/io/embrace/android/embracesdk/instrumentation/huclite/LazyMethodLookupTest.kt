package io.embrace.android.embracesdk.instrumentation.huclite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Method

internal class LazyMethodLookupTest {

    private val errors = mutableListOf<Throwable>()

    @Test
    fun `lookup failure is reported once`() {
        val missingMethod: Method by LazyMethodLookup(Any::class.java, "missingMethod", arrayOf(), errors::add)
        val first = assertThrows(IOException::class.java) { missingMethod }
        val second = assertThrows(IOException::class.java) { missingMethod }
        assertSame(first, second)
        assertSame(first, errors.single())
        assertTrue(first.cause is NoSuchMethodException)
    }

    @Test
    fun `successful lookup reports nothing`() {
        val toString: Method by LazyMethodLookup(Any::class.java, "toString", arrayOf(), errors::add)
        assertEquals("toString", toString.name)
        assertTrue(errors.isEmpty())
    }
}
