package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.capture.memory.EmbraceMemoryService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class EmbraceMemoryServiceTest {

    private lateinit var embraceMemoryService: EmbraceMemoryService
    private val fakeClock = FakeClock()

    @Before
    fun setUp() {
        fakeClock.setCurrentTime(100L)
        embraceMemoryService = EmbraceMemoryService(fakeClock)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `onMemoryWarning populates memoryTimestamps if the offset is less than 100`() {
        with(embraceMemoryService) {
            repeat(100) {
                onMemoryWarning()
                fakeClock.tick()
            }
            val result = this.getCapturedData()
            assertEquals(result.size, 100)
            onMemoryWarning()
            assertEquals(result.size, 100)
        }
    }

    @Test
    fun testCleanCollections() {
        embraceMemoryService.onMemoryWarning()
        assertEquals(1, embraceMemoryService.getCapturedData().size)
        embraceMemoryService.cleanCollections()
        assertEquals(0, embraceMemoryService.getCapturedData().size)
    }
}
