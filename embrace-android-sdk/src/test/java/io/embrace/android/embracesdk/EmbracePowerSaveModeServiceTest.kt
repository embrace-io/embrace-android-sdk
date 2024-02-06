package io.embrace.android.embracesdk

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED
import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.capture.powersave.EmbracePowerSaveModeService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.system.mockContext
import io.embrace.android.embracesdk.fakes.system.mockIntent
import io.embrace.android.embracesdk.fakes.system.mockPowerManager
import io.embrace.android.embracesdk.worker.BackgroundWorker
import io.mockk.clearAllMocks
import io.mockk.every
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

internal class EmbracePowerSaveModeServiceTest {

    private lateinit var service: EmbracePowerSaveModeService

    companion object {
        private lateinit var context: Context
        private lateinit var intent: Intent
        private lateinit var worker: BackgroundWorker
        private lateinit var fakeClock: FakeClock
        private lateinit var powerManager: PowerManager

        /**
         * Setup before all tests get executed. Create mocks here.
         */
        @BeforeClass
        @JvmStatic
        fun setupBeforeAll() {
            context = mockContext()
            intent = mockIntent()
            worker = BackgroundWorker(MoreExecutors.newDirectExecutorService())
            fakeClock = FakeClock()
            powerManager = mockPowerManager()
        }

        /**
         * Setup after all tests get executed. Un-mock all here.
         */
        @AfterClass
        @JvmStatic
        fun tearDownAfterAll() {
            unmockkAll()
        }
    }

    /**
     * Setup before each test.
     */
    @Before
    fun setup() {
        service = EmbracePowerSaveModeService(
            context,
            worker,
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
        every { context.getSystemService(Context.POWER_SERVICE) } returns powerManager
        every { intent.action } returns ACTION_POWER_SAVE_MODE_CHANGED
        every { powerManager.isPowerSaveMode } returns true
        fakeClock.setCurrentTime(111111L)

        service.onReceive(context, intent)
        every { powerManager.isPowerSaveMode } returns false

        service.onReceive(context, intent)
        val intervals = service.getCapturedData()

        assertEquals(1, intervals.size)
        assertTrue(intervals[0].startTime != 0L)
        assertTrue(intervals[0].endTime != null)
    }

    @Test
    fun `test onReceive start interval no end`() {
        every { context.getSystemService(Context.POWER_SERVICE) } returns powerManager
        every { intent.action } returns ACTION_POWER_SAVE_MODE_CHANGED
        every { powerManager.isPowerSaveMode } returns true
        fakeClock.setCurrentTime(111111L)
        service.onReceive(context, intent)

        val intervals = service.getCapturedData()
        assertEquals(1, intervals.size)
        assertTrue(intervals[0].startTime != 0L)
        assertTrue(intervals[0].endTime == null)
    }

    @Test
    fun `test start session on power save mode no end`() {
        every { context.getSystemService(Context.POWER_SERVICE) } returns powerManager
        every { powerManager.isPowerSaveMode } returns true
        fakeClock.setCurrentTime(111111L)
        val startTime = fakeClock.now()
        service.onForeground(true, startTime)

        val intervals = service.getCapturedData()
        assertEquals(1, intervals.size)
        assertTrue(intervals[0].startTime != 0L)
        assertTrue(intervals[0].endTime == null)
    }

    @Test
    fun `test start session on power save mode end saving mode in session`() {
        every { context.getSystemService(Context.POWER_SERVICE) } returns powerManager
        every { powerManager.isPowerSaveMode } returns true
        fakeClock.setCurrentTime(111111L)
        val startTime = fakeClock.now()
        service.onForeground(true, startTime)

        every { intent.action } returns ACTION_POWER_SAVE_MODE_CHANGED
        every { powerManager.isPowerSaveMode } returns false
        service.onReceive(context, intent)

        val intervals = service.getCapturedData()
        assertEquals(1, intervals.size)
        assertTrue(intervals[0].startTime != 0L)
        assertTrue(intervals[0].endTime != null)
    }

    @Test
    fun `test onReceive throws an exception`() {
        every { intent.action } throws Exception()
        fakeClock.setCurrentTime(111111L)
        assertThrows(Exception::class.java) { service.onReceive(context, intent) }
    }

    @Test
    @Throws(InterruptedException::class)
    fun `test receiver can be registered and unregistered`() {
        verify { context.registerReceiver(service, any()) }
        service.close()
        verify { context.unregisterReceiver(service) }
    }

    @Test
    fun testCleanCollections() {
        every { context.getSystemService(Context.POWER_SERVICE) } returns powerManager
        every { powerManager.isPowerSaveMode } returns true
        fakeClock.setCurrentTime(111111L)
        val startTime = fakeClock.now()
        service.onForeground(true, startTime)

        assertEquals(1, service.getCapturedData().size)
        service.cleanCollections()
        assertEquals(0, service.getCapturedData().size)
    }

    @Test
    fun `test limit exceeded`() {
        every { context.getSystemService(Context.POWER_SERVICE) } returns powerManager
        every { intent.action } returns ACTION_POWER_SAVE_MODE_CHANGED
        every { powerManager.isPowerSaveMode } returns false

        repeat(150) {
            service.onReceive(context, intent)
        }
        assertEquals(100, service.getCapturedData().size)
    }
}
