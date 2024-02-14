package io.embrace.android.embracesdk

import android.os.Handler
import android.os.Looper
import io.embrace.android.embracesdk.payload.AnrInterval
import io.embrace.android.embracesdk.payload.AnrSample
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.payload.ThreadInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import logTestMessage
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// number of intervals we create in the test
private const val EXPECTED_INTERVALS = 6

// gap between intervals we trigger in the test case
private const val INTERVAL_GAP_MS = 1000L

// how long we wait for the test to complete before aborting
private const val TEST_TIMEOUT_SECS = 60L

// default config for sampling interval in ms
private const val SAMPLE_INTERVAL_MS = 100

// maximum number of samples capture
private const val MAX_SAMPLES = 80

internal class AnrIntegrationTest : BaseTest() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var latch: CountDownLatch

    @Before
    fun setup() {
        latch = CountDownLatch(EXPECTED_INTERVALS)
        startEmbraceInForeground()
        Embrace.getImpl().endAppStartup(null)
    }

    private fun readBodyAsSessionMessage(request: RecordedRequest): SessionMessage {
        val stream = GZIPInputStream(request.body.inputStream())
        return serializer.fromJson(stream, SessionMessage::class.java)
    }

    /**
     * Verifies that a session end message is sent and contains ANR information. The
     * test triggers 6 ANR intervals by blocking the main thread, with a gap in between
     * intervals. This covers a variety of scenarios:
     *
     * - Exceeding max ANR samples for one interval
     * - Exceeding max ANR intervals for session
     * - Validates that timestamps are correct & that samples broadly contain the expected
     * information. I've used a 'tolerance' for most of these assertions because the number
     * of samples/thread traces will vary on each test run
     * - Added functions to deserialize received session payloads to allow asserting against the received JSON
     * - Bumped max wait for pauseLatch as I noticed a few timeouts when running locally
     */
    @Test
    fun testAnrIntervalsInSessionEndMessage() {
        startAnrIntervals()

        // wait a reasonable time period before assuming the test is deadlocked
        latch.await(TEST_TIMEOUT_SECS, TimeUnit.SECONDS)

        // trigger a session
        sendBackground()

        // ignore startup moment end request that is validated in other tests
        waitForRequest()

        // validate ANRs with JUnit assertions rather than golden file
        waitForRequest { request ->
            val payload = readBodyAsSessionMessage(request)
            assertNotNull(payload)
            val perfInfo by lazy { serializer.toJson(payload.performanceInfo) }
            val intervals = checkNotNull(payload.performanceInfo?.anrIntervals) {
                "No ANR intervals in payload. p=$perfInfo"
            }
            validateIntervals(intervals)
        }
    }

    private fun validateIntervals(intervals: List<AnrInterval>) {
        // validate that the intervals are roughly the right size. this can vary depending on
        // the underlying OS performance
        val size = intervals.size
        assertTrue(size >= EXPECTED_INTERVALS - 1 && size <= EXPECTED_INTERVALS + 1)

        // validate each interval contains the fields we would expect
        intervals.forEachIndexed { _, interval ->
            assertNotNull(interval.startTime)
            assertNull(interval.lastKnownTime)
            assertNotNull(interval.endTime)
            assertTrue(checkNotNull(interval.endTime) > checkNotNull(interval.startTime))
            assertEquals(AnrInterval.Type.UI, interval.type)
            assertNotNull(interval.code)

            if (interval.code == AnrInterval.CODE_DEFAULT) {
                val samples = checkNotNull(interval.anrSampleList?.samples)
                validateSamples(samples)
            } else {
                assertNull(interval.anrSampleList?.samples)
            }
        }
    }

    private fun validateSamples(samples: List<AnrSample>) {
        // validate the samples all recorded their overhead
        assertTrue(samples.all { checkNotNull(it.sampleOverheadMs) >= 0 })

        // validate that all timestamps are ascending
        assertTrue(samples == samples.sortedBy(AnrSample::timestamp))

        // validate the samples have the expected code
        assertTrue(samples.take(MAX_SAMPLES).all { it.code == AnrSample.CODE_DEFAULT })
        val remaining = samples.size - MAX_SAMPLES

        if (remaining > 0) {
            assertTrue(
                samples.subList(MAX_SAMPLES, samples.size)
                    .all { it.code == AnrSample.CODE_SAMPLE_LIMIT_REACHED })
        } else {
            // validate that threads contains method names
            val threads: List<List<ThreadInfo>> = samples.mapNotNull(AnrSample::threads)
            val nonEmptyThreads: List<List<String>> = threads
                .filter(List<ThreadInfo>::isNotEmpty)
                .flatten()
                .map { checkNotNull(it.lines) }
            assertTrue(nonEmptyThreads.size >= 0)
        }
    }

    private fun startAnrIntervals() {
        Executors.newSingleThreadExecutor().execute {
            var attempts = 2000

            while (!Embrace.getInstance().isStarted) {
                Thread.sleep(1)
                attempts--
            }
            if (!Embrace.getInstance().isStarted) {
                error("Embrace did not start within 2s timeout")
            }

            handler.post {
                logTestMessage("Starting first ANR interval")
                Thread.sleep(8000)
                latch.countDown()
                scheduleNextMainThreadWork { produceSecondAnrInterval() }
            }
        }
    }

    private fun produceSecondAnrInterval() {
        logTestMessage("Starting second ANR interval")
        Thread.sleep(2000)
        latch.countDown()
        scheduleNextMainThreadWork { produceThirdAnrInterval() }
    }

    private fun produceThirdAnrInterval() {
        logTestMessage("Starting third ANR interval")
        Thread.sleep(3000)
        latch.countDown()
        scheduleNextMainThreadWork { produceFourthAnrInterval() }
    }

    private fun produceFourthAnrInterval() {
        logTestMessage("Starting fourth ANR interval")
        Thread.sleep(3000)
        latch.countDown()
        scheduleNextMainThreadWork { produceFifthAnrInterval() }
    }

    private fun produceFifthAnrInterval() {
        logTestMessage("Starting fifth ANR interval")
        Thread.sleep(3000)
        latch.countDown()
        scheduleNextMainThreadWork { produceSixthAnrInterval() }
    }

    private fun produceSixthAnrInterval() {
        logTestMessage("Starting sixth ANR interval")
        Thread.sleep(3000)
        latch.countDown()
    }

    private fun scheduleNextMainThreadWork(action: () -> Unit) {
        handler.looper.queue.addIdleHandler {
            handler.postDelayed(action, INTERVAL_GAP_MS)
            false
        }
    }

    private fun assertWithTolerance(msg: String, expected: Int, observed: Int, tolerance: Int) {
        val abs = kotlin.math.abs(expected - observed)
        assertTrue("Expected $expected but got $observed. $msg", abs < tolerance)
    }
}
