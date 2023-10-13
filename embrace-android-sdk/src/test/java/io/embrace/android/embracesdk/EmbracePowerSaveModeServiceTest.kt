package io.embrace.android.embracesdk

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED
import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.capture.powersave.EmbracePowerSaveModeService
import io.embrace.android.embracesdk.fakes.FakeActivityService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.session.ActivityListener
import io.embrace.android.embracesdk.session.ActivityService
import io.embrace.android.embracesdk.session.MemoryCleanerService
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.util.concurrent.ExecutorService

internal class EmbracePowerSaveModeServiceTest {

    private lateinit var service: EmbracePowerSaveModeService

    companion object {
        private lateinit var mockContext: Context
        private lateinit var mockIntent: Intent
        private lateinit var mockCleanerService: MemoryCleanerService
        private lateinit var executor: ExecutorService
        private lateinit var fakeClock: FakeClock
        private lateinit var powerManager: PowerManager
        private lateinit var activityService: ActivityService
        private lateinit var activityListener: ActivityListener

        /**
         * Setup before all tests get executed. Create mocks here.
         */
        @BeforeClass
        @JvmStatic
        fun setupBeforeAll() {
            mockContext = mockk(relaxUnitFun = true)
            mockIntent = mockk()
            mockCleanerService = mockk(relaxUnitFun = true)
            executor = MoreExecutors.newDirectExecutorService()
            fakeClock = FakeClock()
            powerManager = mockk()
            activityService = FakeActivityService()
            activityListener = mockk()
        }

        /**
         * Setup after all tests get executed. Un-mock all here.
         */
        @AfterClass
        @JvmStatic
        fun tearDownAfterAll() {
            unmockkAll()
            executor.shutdown()
        }
    }

    /**
     * Setup before each test.
     */
    @Before
    fun setup() {
        service = EmbracePowerSaveModeService(
            mockContext,
            executor,
            fakeClock,
            powerManager
        )
    }

    /**
     * Setup after each test. Clean mocks content.
     */
    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `test onReceive adds a low battery interval correctly`() {
        every { mockContext.getSystemService(Context.POWER_SERVICE) } returns powerManager
        every { mockIntent.action } returns ACTION_POWER_SAVE_MODE_CHANGED
        every { powerManager.isPowerSaveMode } returns true
        fakeClock.setCurrentTime(111111L)

        service.onReceive(mockContext, mockIntent)
        every { powerManager.isPowerSaveMode } returns false

        service.onReceive(mockContext, mockIntent)
        val intervals = service.getCapturedData()

        assertEquals(1, intervals.size)
        assertTrue(intervals[0].startTime != 0L)
        assertTrue(intervals[0].endTime != null)
    }

    @Test
    fun `test onReceive start interval no end`() {
        every { mockContext.getSystemService(Context.POWER_SERVICE) } returns powerManager
        every { mockIntent.action } returns ACTION_POWER_SAVE_MODE_CHANGED
        every { powerManager.isPowerSaveMode } returns true
        fakeClock.setCurrentTime(111111L)
        service.onReceive(mockContext, mockIntent)

        val intervals = service.getCapturedData()
        assertEquals(1, intervals.size)
        assertTrue(intervals[0].startTime != 0L)
        assertTrue(intervals[0].endTime == null)
    }

    @Test
    fun `test start session on power save mode no end`() {
        every { mockContext.getSystemService(Context.POWER_SERVICE) } returns powerManager
        every { powerManager.isPowerSaveMode } returns true
        fakeClock.setCurrentTime(111111L)
        val startTime = fakeClock.now()
        service.onForeground(true, startTime, startTime)

        val intervals = service.getCapturedData()
        assertEquals(1, intervals.size)
        assertTrue(intervals[0].startTime != 0L)
        assertTrue(intervals[0].endTime == null)
    }

    @Test
    fun `test start session on power save mode end saving mode in session`() {
        every { mockContext.getSystemService(Context.POWER_SERVICE) } returns powerManager
        every { powerManager.isPowerSaveMode } returns true
        fakeClock.setCurrentTime(111111L)
        val startTime = fakeClock.now()
        service.onForeground(true, startTime, startTime)

        every { mockIntent.action } returns ACTION_POWER_SAVE_MODE_CHANGED
        every { powerManager.isPowerSaveMode } returns false
        service.onReceive(mockContext, mockIntent)

        val intervals = service.getCapturedData()
        assertEquals(1, intervals.size)
        assertTrue(intervals[0].startTime != 0L)
        assertTrue(intervals[0].endTime != null)
    }

    @Test
    fun `test onReceive throws an exception`() {
        every { mockIntent.action } throws Exception()
        fakeClock.setCurrentTime(111111L)
        assertThrows(Exception::class.java) { service.onReceive(mockContext, mockIntent) }
    }

    @Test
    @Throws(InterruptedException::class)
    fun `test receiver can be registered and unregistered`() {
        verify { mockContext.registerReceiver(service, any()) }
        service.close()
        verify { mockContext.unregisterReceiver(service) }
    }

    @Test
    fun testCleanCollections() {
        every { mockContext.getSystemService(Context.POWER_SERVICE) } returns powerManager
        every { powerManager.isPowerSaveMode } returns true
        fakeClock.setCurrentTime(111111L)
        val startTime = fakeClock.now()
        service.onForeground(true, startTime, startTime)

        assertEquals(1, service.getCapturedData().size)
        service.cleanCollections()
        assertEquals(0, service.getCapturedData().size)
    }
}
