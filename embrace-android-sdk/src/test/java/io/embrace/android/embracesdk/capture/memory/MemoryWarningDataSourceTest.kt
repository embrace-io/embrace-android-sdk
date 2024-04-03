package io.embrace.android.embracesdk.capture.memory

import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import org.junit.Assert
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
            Assert.assertEquals("emb-memory-warning", this.schemaType.name)
            Assert.assertEquals(15000000000.millisToNanos(), spanStartTimeMs)
        }
    }
}
