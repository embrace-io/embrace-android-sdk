package io.embrace.android.embracesdk

import android.app.ActivityManager
import io.embrace.android.embracesdk.capture.memory.EmbraceMemoryService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.session.MemoryCleanerService
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

internal class EmbraceMemoryServiceTest {

    private lateinit var embraceMemoryService: EmbraceMemoryService
    private var activityManager: ActivityManager? = null
    private var memoryCleanerService: MemoryCleanerService? = null
    private val fakeClock = FakeClock()

    @Before
    fun setUp() {
        activityManager = mockk(relaxUnitFun = true)
        memoryCleanerService = mockk(relaxUnitFun = true)
        fakeClock.setCurrentTime(100L)
        embraceMemoryService = EmbraceMemoryService(fakeClock)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test memory service initialization throws an exception if activityManager is null`() {
        memoryCleanerService = null
        activityManager = null
        assertThrows(
            IllegalStateException::class.java
        ) {
            checkNotNull(memoryCleanerService) { "memoryCleanerService must not be null" }
        }
    }

    @Test
    fun `test memory service initialization throws an exception if memoryCleanerService is null`() {
        activityManager = null
        assertThrows(
            IllegalStateException::class.java
        ) {
            checkNotNull(activityManager) { "activityManager must not be null" }
        }
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
