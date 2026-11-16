package io.embrace.android.embracesdk.internal.store.file

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

internal class WriteBarrierTest {

    private val ran = mutableListOf<String>()

    @Test
    fun `holds writes until opened, runs them once in order, then runs later writes at once`() {
        val barrier = WriteBarrier()
        barrier.runWhenOpen { ran.add("a") }
        barrier.runWhenOpen { ran.add("b") }
        assertEquals(emptyList<String>(), ran)

        barrier.open()
        barrier.open()
        barrier.runWhenOpen { ran.add("c") }
        assertEquals(listOf("a", "b", "c"), ran)
    }

    @Test
    fun `a failing write doesn't stop the rest`() {
        val barrier = WriteBarrier()
        barrier.runWhenOpen { error("rejected") }
        barrier.runWhenOpen { ran.add("b") }
        assertThrows(IllegalStateException::class.java) { barrier.open() }
        assertEquals(listOf("b"), ran)
    }
}
