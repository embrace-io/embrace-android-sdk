package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.anr.ndk.EmbraceNativeThreadSamplerService
import io.embrace.android.embracesdk.anr.ndk.isUnityMainThread
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.behavior.AnrBehavior
import io.embrace.android.embracesdk.config.remote.AllowedNdkSampleMethod
import io.embrace.android.embracesdk.config.remote.AnrRemoteConfig
import io.embrace.android.embracesdk.config.remote.Unwinder
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDeviceArchitecture
import io.embrace.android.embracesdk.fakes.fakeAnrBehavior
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.payload.NativeThreadAnrInterval
import io.embrace.android.embracesdk.payload.NativeThreadAnrSample
import io.embrace.android.embracesdk.payload.NativeThreadAnrStackframe
import io.embrace.android.embracesdk.payload.mapThreadState
import io.embrace.android.embracesdk.worker.ScheduledWorker
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Random
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

internal class EmbraceNativeThreadSamplerServiceTest {

    private lateinit var sampler: EmbraceNativeThreadSamplerService
    private lateinit var sharedObjectLoader: SharedObjectLoader
    private lateinit var configService: ConfigService
    private lateinit var delegate: EmbraceNativeThreadSamplerService.NdkDelegate
    private lateinit var random: Random
    private lateinit var executorService: BlockingScheduledExecutorService
    private val obj = NativeThreadAnrInterval(null, null, null, null, null, null, null, null)
    private val testSample = NativeThreadAnrSample(null, null, null, null)
    private lateinit var anrBehavior: AnrBehavior
    private lateinit var cfg: AnrRemoteConfig

    @Before
    fun setUp() {
        cfg = AnrRemoteConfig(pctNativeThreadAnrSamplingEnabled = 100f)
        anrBehavior = fakeAnrBehavior { cfg }
        configService = FakeConfigService(anrBehavior = anrBehavior)
        sharedObjectLoader = mockk(relaxed = true)
        delegate = mockk(relaxed = true)
        val logger = EmbLoggerImpl()
        random = mockk(relaxed = true)
        executorService = BlockingScheduledExecutorService()
        sampler =
            EmbraceNativeThreadSamplerService(
                configService,
                lazy { emptyMap() },
                random,
                logger,
                delegate,
                ScheduledWorker(executorService),
                FakeDeviceArchitecture(),
                sharedObjectLoader
            )
        every { random.nextInt(any()) } returns 0
        every { sharedObjectLoader.loadEmbraceNative() } returns true
    }

    @Test
    fun testIsNotUnityMainThread() {
        assertFalse(sampler.setupNativeSampler())
        assertFalse(isUnityMainThread())
        verify(exactly = 1) { delegate.setupNativeThreadSampler(true) }
    }

    @Test
    fun testIsUnityMainThread() {
        val threadFactory = ThreadFactory { runnable ->
            Executors.defaultThreadFactory().newThread(runnable::run).apply {
                name = "UnityMain"
            }
        }
        val future = Executors.newSingleThreadExecutor(threadFactory).submit {
            assertTrue(isUnityMainThread())
            assertFalse(sampler.setupNativeSampler())
            verify(exactly = 1) { delegate.setupNativeThreadSampler(true) }
        }
        future.get()
    }

    @Test
    fun testSessionEndDisabledSampling() {
        cfg = cfg.copy(pctNativeThreadAnrSamplingEnabled = 0f)
        sampler.intervals = mutableListOf(obj)
        simulateUnityThreadSample()
        assertNull(sampler.getCapturedIntervals(false))
    }

    @Test
    fun testSessionEndListener() {
        sampler.onThreadBlocked(Thread.currentThread(), 0)
        while (!sampler.sampling) {
            sampler.onThreadBlockedInterval(Thread.currentThread(), 0)
        }
        sampler.intervals.add(
            NativeThreadAnrInterval(
                null,
                null,
                null,
                null,
                null,
                mutableListOf(),
                null,
                null
            )
        )
        every { delegate.finishSampling() } returns listOf(testSample)

        val nativeThreadAnrIntervals = sampler.getCapturedIntervals(false)
        val interval = nativeThreadAnrIntervals?.single()
        val trace = checkNotNull(interval?.samples)
        assertTrue(trace.isNotEmpty())
    }

    /**
     * If an ANR interval occurred but no unity sample was created, then don't report
     * anything in the session
     */
    @Test
    fun testSessionWithoutUsefulSamples() {
        // don't include a stacktrace in the sample.
        sampler.onThreadBlocked(Thread.currentThread(), 0)
        assertNull(sampler.getCapturedIntervals(false))
    }

    @Test
    fun testEmptySamplesFilteredOut() {
        // include a stacktrace in the first sample
        simulateUnityThreadSample()

        // don't include a stacktrace in subsequent samples
        sampler.onThreadBlocked(Thread.currentThread(), 0)

        val nativeThreadAnrIntervals = sampler.getCapturedIntervals(false)
        val interval = nativeThreadAnrIntervals?.single()
        val trace = checkNotNull(interval?.samples)
        assertTrue(trace.isNotEmpty())
    }

    @Test
    fun testMaxCaptureLimitsExceeded() {
        val sampleCount = 20
        val testSamples = (0 until sampleCount).map { testSample }
        every { delegate.finishSampling() } returns testSamples

        // simulate capturing a ridiculous number of ANR samples
        repeat(100) { count ->
            val timestamp = count.toLong()
            sampler.onThreadBlocked(Thread.currentThread(), timestamp)
            sampler.factor = 1

            // ANR takes 20s to complete each time, producing 20 samples
            val thread = Thread.currentThread()
            sampler.onThreadBlockedInterval(thread, timestamp)
            sampler.onThreadUnblocked(thread, timestamp)
            executorService.runAllSubmittedTasks()
        }

        // finish all pending scheduled jobs
        executorService.runAllSubmittedTasks()

        // verify data was captured up to the default limit of 5
        val nativeThreadAnrIntervals = sampler.getCapturedIntervals(false)

        // verify same data was included in the session
        val intervals = checkNotNull(nativeThreadAnrIntervals)

        assertEquals(
            5,
            intervals.size
        )
        assertTrue(
            intervals.all {
                val sample = checkNotNull(it.samples)
                sample.size == sampleCount
            }
        )
    }

    @Test
    fun testIgnoreAllowlist() {
        val thread = Thread.currentThread()
        assertTrue(sampler.containsAllowedStackframes(anrBehavior, thread.stackTrace))
        assertTrue(
            sampler.containsAllowedStackframes(
                anrBehavior,
                thread.stackTrace
            )
        )
    }

    @Test
    fun testRespectsAllowlist() {
        cfg = AnrRemoteConfig(
            nativeThreadAnrSamplingAllowlist = listOf(
                AllowedNdkSampleMethod(
                    "com.unity3d.player.UnityPlayer",
                    "pauseUnity"
                ),
                AllowedNdkSampleMethod("io.example.CustomClz", "customMethod")
            ),
            ignoreNativeThreadAnrSamplingAllowlist = false
        )

        val unityPlayer =
            StackTraceElement("com.unity3d.player.UnityPlayer", "pauseUnity", null, -1)
        val fail1 = StackTraceElement("com.unity3d.player.UnityPlayer", "foo", null, -1)
        val fail2 = StackTraceElement("io.example.CustomClz", "pauseUnity", null, -1)
        val fail3 = StackTraceElement("java.lang.String", "toString", null, -1)
        assertTrue(sampler.containsAllowedStackframes(anrBehavior, arrayOf(unityPlayer)))
        assertFalse(sampler.containsAllowedStackframes(anrBehavior, arrayOf(fail1)))
        assertFalse(sampler.containsAllowedStackframes(anrBehavior, arrayOf(fail2)))
        assertFalse(sampler.containsAllowedStackframes(anrBehavior, arrayOf(fail3)))
    }

    @Test
    fun testThreadBlockedAllowListTrue() {
        assertEquals(-1, sampler.count)
        assertEquals(-1, sampler.factor)
        assertTrue(sampler.ignored)

        val currentThread = Thread.currentThread()
        sampler.onThreadBlocked(currentThread, 1500000)
        sampler.onThreadBlockedInterval(currentThread, 1500000)
        verify(exactly = 1) { delegate.startSampling(0, 500) }
        assertEquals(1, sampler.count)
        assertEquals(5, sampler.factor)
        assertFalse(sampler.ignored)

        with(sampler.currentInterval) {
            checkNotNull(this)
            assertEquals(0L, sampleOffsetMs)
            assertEquals(1500000L, threadBlockedTimestamp)
            assertEquals(currentThread.id, id)
            assertEquals(currentThread.name, name)
            assertEquals(mapThreadState(currentThread.state).code, state)
            assertEquals(currentThread.priority, priority)
        }
    }

    @Test
    fun testThreadBlockedAllowListFalse() {
        cfg = cfg.copy(
            ignoreNativeThreadAnrSamplingAllowlist = false
        )
        assertEquals(-1, sampler.count)
        assertEquals(-1, sampler.factor)
        assertTrue(sampler.ignored)

        sampler.onThreadBlocked(Thread.currentThread(), 0)
        verify(exactly = 0) { delegate.startSampling(any(), any()) }
        assertEquals(-1, sampler.count)
        assertEquals(-1, sampler.factor)
        assertTrue(sampler.ignored)
    }

    @Test
    fun testThreadBlockedDisabledSampling() {
        cfg = cfg.copy(
            pctNativeThreadAnrSamplingEnabled = 0f
        )

        sampler.onThreadBlocked(Thread.currentThread(), 0)
        verify(exactly = 0) { delegate.startSampling(any(), any()) }
        assertEquals(-1, sampler.count)
        assertEquals(-1, sampler.factor)
        assertTrue(sampler.ignored)
    }

    @Test
    fun testOnIntervalIgnoredAllowlist() {
        cfg = cfg.copy(
            ignoreNativeThreadAnrSamplingAllowlist = false
        )
        sampler.onThreadBlocked(Thread.currentThread(), 0)

        repeat(10) {
            sampler.onThreadBlockedInterval(Thread.currentThread(), 0)
        }
        verify(exactly = 0) { delegate.startSampling(0, 500L) }
    }

    @Test
    fun testOnIntervalDisabledSampling() {
        cfg = cfg.copy(
            pctNativeThreadAnrSamplingEnabled = 0f
        )

        sampler.onThreadBlocked(Thread.currentThread(), 0)
        repeat(10) {
            assertEquals(-1, sampler.count)
            sampler.onThreadBlockedInterval(Thread.currentThread(), 0)
        }
        verify(exactly = 0) { delegate.startSampling(any(), any()) }
        assertEquals(-1, sampler.count)
    }

    @Test
    fun testOnIntervalSampled() {
        sampler.onThreadBlocked(Thread.currentThread(), 0)

        repeat(6) { k ->
            assertEquals(k, sampler.count)
            sampler.onThreadBlockedInterval(Thread.currentThread(), 0)
        }
        verify(exactly = 1) { delegate.startSampling(0, 500L) }
        assertEquals(1, sampler.count)
    }

    @Test
    fun testNonZeroOffset() {
        val shift = 3
        every { random.nextInt(any()) } returns shift
        sampler.onThreadBlocked(Thread.currentThread(), 0)
        val startPos = 2
        assertEquals(startPos, sampler.count)

        repeat(4) { k ->
            assertEquals(startPos + k, sampler.count)
            sampler.onThreadBlockedInterval(Thread.currentThread(), 0)
        }
        verify(exactly = 1) { delegate.startSampling(0, 500L) }
        assertEquals(1, sampler.count)
    }

    @Test
    fun testCleanSamples() {
        val ref = mutableListOf<NativeThreadAnrInterval>()
        sampler.intervals = ref
        sampler.cleanCollections()
        assertNotSame(sampler.intervals, ref)
    }

    @Test
    fun testTickRecorded() {
        val shift = 5
        every { delegate.finishSampling() } returns listOf(testSample)
        every { random.nextInt(any()) } returns shift

        val thread = Thread.currentThread()
        sampler.onThreadBlocked(thread, 14993000L)

        repeat(1) {
            sampler.onThreadBlockedInterval(thread, 14999000L)
        }

        sampler.onThreadUnblocked(thread, 15000000L)
        executorService.runAllSubmittedTasks()

        // verify info recorded.
        val tick = sampler.intervals.single()
        assertEquals(500L, tick.sampleOffsetMs)
        assertEquals(14993000L, tick.threadBlockedTimestamp)
        assertEquals(thread.id, tick.id)
        assertEquals(thread.name, tick.name)
        assertEquals(mapThreadState(thread.state).code, tick.state)
        assertEquals(thread.priority, tick.priority)
        assertEquals(Unwinder.LIBUNWIND.code, tick.unwinder)

        val obj = checkNotNull(tick.samples?.single())
        assertEquals(testSample, obj)
    }

    @Test
    fun testDupeStacktraceCollection() {
        val thread = Thread.currentThread()
        sampler.onThreadBlocked(thread, 14993000L)

        // create the first sample
        val frame1 = NativeThreadAnrStackframe(
            "0x5823409a",
            "0x00340f204",
            "/data/foo.so",
            11
        )
        val frame2 = NativeThreadAnrStackframe(
            "0x2823449a",
            "0x10320f520",
            "/data/bar.so",
            9
        )
        every { delegate.finishSampling() } returns listOf(
            NativeThreadAnrSample(
                0,
                0,
                0,
                listOf(frame1)
            ),
            NativeThreadAnrSample(
                0,
                0,
                0,
                listOf(frame1)
            ),
            NativeThreadAnrSample(
                0,
                0,
                0,
                listOf(frame2)
            )
        )
        sampler.onThreadBlockedInterval(thread, 14993000L)
        sampler.onThreadUnblocked(thread, 14993000L)
        executorService.runAllSubmittedTasks()

        // assert the frames were recorded
        val currentSample = checkNotNull(sampler.currentInterval)
        val traces = checkNotNull(currentSample.samples)
        assertEquals(3, traces.size)
        assertEquals(listOf(frame1), traces[0].stackframes)
        assertEquals(listOf(frame1), traces[1].stackframes)
        assertEquals(listOf(frame2), traces[2].stackframes)
    }

    @Test
    fun testMaxIntervalLimit() {
        every { delegate.finishSampling() } returns listOf(testSample)

        val factor = checkNotNull(configService.anrBehavior.getNativeThreadAnrSamplingFactor())
        val thread = Thread.currentThread()

        repeat(10) {
            sampler.onThreadBlocked(thread, 14993000L)
            sampler.count = factor
            sampler.onThreadBlockedInterval(thread, 14993000L)
            sampler.onThreadUnblocked(thread, 14993000L)
        }
        assertEquals(5, sampler.intervals.size)
    }

    private fun simulateUnityThreadSample() {
        sampler.onThreadBlocked(Thread.currentThread(), 0)
        sampler.currentInterval?.samples?.add(testSample)
    }
}
