package io.embrace.android.gradle.plugin.instrumentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassInstrumentationFilterTest {

    @Test
    fun `valid patterns skip matching classes`() {
        val filter = ClassInstrumentationFilter(listOf("^com\\.example\\..*"))
        assertTrue(filter.shouldSkip("com.example.Foo"))
        assertFalse(filter.shouldSkip("org.example.Foo"))
    }

    @Test
    fun `invalid pattern fails on construction and names the pattern`() {
        val exc = assertThrows(IllegalArgumentException::class.java) {
            ClassInstrumentationFilter(listOf("com.example.*", "com.example.[Foo"))
        }
        val msg = checkNotNull(exc.message)
        assertTrue(msg, msg.contains("'com.example.[Foo'"))
        assertTrue(msg, msg.contains("classIgnorePatterns"))
    }
}
