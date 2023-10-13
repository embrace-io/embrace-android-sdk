package io.embrace.android.embracesdk.anr.detection

import android.app.ActivityManager
import io.embrace.android.embracesdk.clock.Clock
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.remote.AnrRemoteConfig
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.fakeAnrBehavior
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class AnrProcessErrorSamplerTest {

    private lateinit var anrProcessErrorSampler: AnrProcessErrorSampler

    companion object {
        private const val ANR_INTERVAL = 100L
        private const val DELAY = 5 * 1000L
        private const val PROCESS_ID = 1

        // change interval so scheduler can get rescheduled
        private const val CHANGED_INTERVAL = ANR_INTERVAL * 2

        private val mockActivityManager: ActivityManager = mockk()
        private var cfg: AnrRemoteConfig = AnrRemoteConfig(
            // 15 seconds since thread is unblocked
            anrProcessErrorsSchedulerExtraTimeAllowance = 15 * 1000,
            anrProcessErrorsDelayMs = DELAY,
            pctAnrProcessErrorsEnabled = 100
        )

        private val configService: ConfigService = FakeConfigService(anrBehavior = fakeAnrBehavior { cfg })
        private val clock: Clock = FakeClock(30 * 1000)
        private val mockLogger: InternalEmbraceLogger = mockk(relaxUnitFun = true)
        private val mockScheduledFuture: ScheduledFuture<*> = mockk(relaxed = true)
        private val anrProcessErrorStateInfo = AnrProcessErrorStateInfo(
            "tag",
            "sMessage",
            "lMessage",
            "stacktrace",
            clock.now()
        )
        private val secondAnrProcessErrorStateInfo = AnrProcessErrorStateInfo(
            "tag2",
            "sMessage2",
            "lMessage2",
            "stacktrace2",
            clock.now()
        )
        private val mockScheduler: ScheduledExecutorService = mockk(relaxed = true)

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            mockkStatic(::findAnrProcessErrorStateInfo)
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            unmockkAll()
        }
    }

    @Before
    fun before() {
        clearAllMocks(answers = false)

        // these are needed here because some tests change it
        cfg = AnrRemoteConfig(
            // 15 seconds since thread is unblocked
            anrProcessErrorsSchedulerExtraTimeAllowance = 15 * 1000,
            anrProcessErrorsDelayMs = DELAY,
            anrProcessErrorsIntervalMs = ANR_INTERVAL,
            pctAnrProcessErrorsEnabled = 100
        )
        every {
            mockScheduler.scheduleAtFixedRate(
                any(),
                DELAY,
                CHANGED_INTERVAL,
                TimeUnit.MILLISECONDS
            )
        } returns mockScheduledFuture

        anrProcessErrorSampler = AnrProcessErrorSampler(
            mockActivityManager,
            configService,
            mockScheduler,
            clock,
            mockLogger,
            PROCESS_ID
        )
        anrProcessErrorSampler.scheduledFuture = mockScheduledFuture
    }

    @Test
    fun `verify scheduler should be allowed to run if thread has not been unblocked yet`() {
        assertTrue(anrProcessErrorSampler.isSchedulerAllowedToRun())
    }

    @Test
    fun `verify scheduler should run if thread is unblocked but maximum threshold has not been reached`() {
        // only 5 seconds have elapsed since thread unblocked
        val threadUnblockedTime = 25000L
        anrProcessErrorSampler.onThreadUnblocked(Thread(), threadUnblockedTime)

        assertTrue(anrProcessErrorSampler.isSchedulerAllowedToRun())
    }

    // with this test we are also verifying that onThreadBlocked works fine
    @Test
    fun `verify scheduler doesn't run if thread has been unblocked and maximum threshold has been reached`() {
        // 25 seconds have elapsed since thread unblocked
        val threadUnblockedTime = 5000L
        anrProcessErrorSampler.onThreadUnblocked(Thread(), threadUnblockedTime)

        assertFalse(anrProcessErrorSampler.isSchedulerAllowedToRun())
    }

    @Test
    fun `verify searchForProcessErrors when scheduler not allowed to run and no anr process error found`() {
        // with this setup scheduler is not allowed to run
        val threadUnblockedTime = 5000L
        anrProcessErrorSampler.onThreadUnblocked(Thread(), threadUnblockedTime)

        // no anr process error found
        every {
            findAnrProcessErrorStateInfo(
                clock,
                mockActivityManager,
                PROCESS_ID
            )
        } returns null

        anrProcessErrorSampler.onSearchForProcessErrors(/* any */1)

        // because scheduler is not allowed to run, it should be cancelled
        verify { mockScheduledFuture.cancel(false) }
        assertTrue(anrProcessErrorSampler.anrProcessErrors.isEmpty())
    }

    @Test
    fun `verify searchForProcessErrors when scheduler not allowed to run and anr process error found`() {
        // with this setup scheduler is not allowed to run
        val threadUnblockedTime = 5000L
        anrProcessErrorSampler.onThreadUnblocked(Thread(), threadUnblockedTime)

        // anr process error found
        every {
            findAnrProcessErrorStateInfo(
                clock,
                mockActivityManager,
                PROCESS_ID
            )
        } returns anrProcessErrorStateInfo

        val timestamp = 1L
        anrProcessErrorSampler.onSearchForProcessErrors(/* any */timestamp)

        // because scheduler is not allowed to run, it should be cancelled
        verify { mockScheduledFuture.cancel(false) }
        assertEquals(
            anrProcessErrorStateInfo,
            anrProcessErrorSampler.anrProcessErrors[timestamp]
        )
    }

    @Test
    fun `verify searchForProcessErrors reschedule finds no anr process error found`() {
        // with this setup scheduler is allowed to run
        val threadUnblockedTime = 25000L
        anrProcessErrorSampler.onThreadUnblocked(Thread(), threadUnblockedTime)
        cfg = cfg.copy(anrProcessErrorsIntervalMs = CHANGED_INTERVAL)

        // no anr process error found
        every {
            findAnrProcessErrorStateInfo(
                clock,
                mockActivityManager,
                PROCESS_ID
            )
        } returns null

        anrProcessErrorSampler.onSearchForProcessErrors(/* any */1)

        // because scheduler is rescheduled, it should first be cancelled
        verify { mockScheduledFuture.cancel(false) }
        assertTrue(anrProcessErrorSampler.anrProcessErrors.isEmpty())
        // verify rescheduling with proper setup
        verify {
            mockScheduler.scheduleAtFixedRate(
                any(),
                DELAY,
                CHANGED_INTERVAL,
                TimeUnit.MILLISECONDS
            )
        }
    }

    @Test
    fun `verify searchForProcessErrors reschedule does not fail if exception thrown`() {
        // with this setup scheduler is allowed to run
        val threadUnblockedTime = 25000L
        anrProcessErrorSampler.onThreadUnblocked(Thread(), threadUnblockedTime)
        cfg = cfg.copy(anrProcessErrorsIntervalMs = CHANGED_INTERVAL)

        // no anr process error found
        every {
            findAnrProcessErrorStateInfo(
                clock,
                mockActivityManager,
                PROCESS_ID
            )
        } returns null
        every {
            mockScheduler.scheduleAtFixedRate(
                any(),
                DELAY,
                CHANGED_INTERVAL,
                TimeUnit.MILLISECONDS
            )
        } throws Exception()

        anrProcessErrorSampler.onSearchForProcessErrors(/* any */1)

        // because scheduler is rescheduled, it should first be cancelled
        verify { mockScheduledFuture.cancel(false) }
        assertTrue(anrProcessErrorSampler.anrProcessErrors.isEmpty())
        // verify rescheduling with proper setup
        verify {
            mockScheduler.scheduleAtFixedRate(
                any(),
                DELAY,
                CHANGED_INTERVAL,
                TimeUnit.MILLISECONDS
            )
        }

        // if test does not fail, it means the exception got swallowed, which is what we're expecting
    }

    @Test
    fun `verify searchForProcessErrors when scheduler interval config changed and anr process error has been found`() {
        // with this setup scheduler is allowed to run
        val threadUnblockedTime = 25000L
        anrProcessErrorSampler.onThreadUnblocked(Thread(), threadUnblockedTime)
        cfg = cfg.copy(anrProcessErrorsIntervalMs = CHANGED_INTERVAL)

        // anr process error found
        every {
            findAnrProcessErrorStateInfo(
                clock,
                mockActivityManager,
                PROCESS_ID
            )
        } returns anrProcessErrorStateInfo

        val timestamp = 1L
        anrProcessErrorSampler.onSearchForProcessErrors(/* any */timestamp)

        // because anr process error found, then it should cancelled
        verify { mockScheduledFuture.cancel(true) }
        assertEquals(
            anrProcessErrorStateInfo,
            anrProcessErrorSampler.anrProcessErrors[timestamp]
        )
        // verify no rescheduling has been performed
        verify(exactly = 0) {
            mockScheduler.scheduleAtFixedRate(
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `verify on thread unblocked`() {
        val unblockedTs = 10L
        anrProcessErrorSampler.onThreadUnblocked(Thread(), unblockedTs)

        assertEquals(unblockedTs, anrProcessErrorSampler.threadUnblockedMs)
    }

    @Test
    fun `verify on thread unblocked should not do anything if feature is disabled`() {
        cfg = cfg.copy(pctAnrProcessErrorsEnabled = 0)
        val unblockedTs = 10L

        anrProcessErrorSampler.onThreadUnblocked(Thread(), unblockedTs)

        assertNull(anrProcessErrorSampler.threadUnblockedMs)
    }

    @Test
    fun `verify on thread blocked interval should not do anything`() {
        anrProcessErrorSampler.onThreadBlockedInterval(Thread(), 1L)

        // verify mocks have not been called
        verify { mockActivityManager wasNot Called }
        verify { mockActivityManager wasNot Called }
        verify { mockLogger wasNot Called }
        verify { mockScheduledFuture wasNot Called }
        verify { mockScheduler wasNot Called }
    }

    @Test
    fun `verify on thread blocked should reset and schedule`() {
        anrProcessErrorSampler.onThreadBlocked(Thread(), 1L)

        assertNull(anrProcessErrorSampler.threadUnblockedMs)
        verify { mockScheduledFuture.cancel(true) }
        assertTrue(anrProcessErrorSampler.anrProcessErrors.isEmpty())
        // verify rescheduling with proper setup
        verify {
            mockScheduler.scheduleAtFixedRate(
                any(),
                DELAY,
                ANR_INTERVAL,
                TimeUnit.MILLISECONDS
            )
        }
    }

    @Test
    fun `verify on thread blocked should not do anything if feature is disabled`() {
        cfg = cfg.copy(pctAnrProcessErrorsEnabled = 0)
        val unblockedTs = 100L
        anrProcessErrorSampler.threadUnblockedMs = unblockedTs
        anrProcessErrorSampler.anrProcessErrors[1] = mockk()

        anrProcessErrorSampler.onThreadBlocked(Thread(), 1L)

        // verify that threadUnblockedMs wasn't reset
        assertEquals(unblockedTs, anrProcessErrorSampler.threadUnblockedMs)
        verify { mockScheduledFuture wasNot Called }
        // verify that anrProcessErrors wasn't reset
        assertTrue(anrProcessErrorSampler.anrProcessErrors.size == 1)
    }

    @Test
    fun `get anr process errors with background anrs`() {
        cfg = cfg.copy(pctBgEnabled = 100)
        val startTime = 30000L
        // this will be a bkg anr
        anrProcessErrorSampler.anrProcessErrors[20000] = anrProcessErrorStateInfo
        anrProcessErrorSampler.anrProcessErrors[40000] = secondAnrProcessErrorStateInfo

        val anrProcessErrors = anrProcessErrorSampler.getAnrProcessErrors(startTime)

        assertEquals(2, anrProcessErrors.size)
        assertEquals(anrProcessErrorStateInfo, anrProcessErrors[0])
        assertEquals(secondAnrProcessErrorStateInfo, anrProcessErrors[1])
    }

    @Test
    fun `get anr process errors without background anrs`() {
        cfg = cfg.copy(pctBgEnabled = 0)
        val startTime = 30000L
        // this will be a bkg anr
        anrProcessErrorSampler.anrProcessErrors[20000] = anrProcessErrorStateInfo
        anrProcessErrorSampler.anrProcessErrors[40000] = secondAnrProcessErrorStateInfo

        val anrProcessErrors = anrProcessErrorSampler.getAnrProcessErrors(startTime)

        assertEquals(1, anrProcessErrors.size)
        assertEquals(secondAnrProcessErrorStateInfo, anrProcessErrors[0])
    }
}
