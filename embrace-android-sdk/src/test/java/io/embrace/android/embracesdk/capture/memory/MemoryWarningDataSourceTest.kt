package io.embrace.android.embracesdk.capture.memory

import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class MemoryWarningDataSourceTest {

    private lateinit var source: MemoryWarningDataSource
    private lateinit var writer: FakeCurrentSessionSpan

    @Before
    fun setUp() {
        writer = FakeCurrentSessionSpan()
        source = MemoryWarningDataSource(writer)
    }

    @Test
    fun addMemoryWarning() {
        source.onMemoryWarning(15000000000)
        with(writer.addedEvents.single()) {
            assertEquals("memory-warning", this.schemaType.name)
            assertEquals(15000000000.millisToNanos(), spanStartTimeMs)
        }
    }

    @Test
    fun `memory warnings have an upper limit`() {
        repeat(150) {
            source.onMemoryWarning(15000000000 + it * 2000)
        }
        assertEquals(EmbraceMemoryService.MAX_CAPTURED_MEMORY_WARNINGS, writer.addedEvents.size)
    }
}
