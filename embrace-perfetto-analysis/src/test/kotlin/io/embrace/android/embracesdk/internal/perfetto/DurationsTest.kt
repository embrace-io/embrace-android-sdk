package io.embrace.android.embracesdk.internal.perfetto

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

internal class DurationsTest {

    private val original: Locale = Locale.getDefault()

    @After
    fun tearDown() {
        Locale.setDefault(original)
    }

    @Test
    fun `nanoseconds read as microseconds, keeping every digit the trace recorded`() {
        assertEquals("12083.458", micros(12_083_458L))
        assertEquals("0.001", micros(1L))
        assertEquals("0.000", micros(0L))
        assertEquals("5.268", micros(5267.670403587444))
    }

    @Test
    fun `the separator is a point wherever this runs, not whatever the machine is set to`() {
        Locale.setDefault(Locale.GERMANY)
        assertEquals("12083.458", micros(12_083_458L))
        assertEquals("5.268", micros(5267.670403587444))
    }
}
