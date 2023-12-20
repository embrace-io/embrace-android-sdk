package io.embrace.android.embracesdk

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.squareup.moshi.Types
import io.embrace.android.embracesdk.BaseTest
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.payload.AnrInterval
import io.embrace.android.embracesdk.payload.AnrSample
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.payload.ThreadInfo
import io.embrace.android.embracesdk.payload.extensions.duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import okhttp3.mockwebserver.RecordedRequest

// number of intervals we create in the test
private const val EXPECTED_INTERVALS = 6

// we allow for extra or missing samples to account for natural differences in thread scheduling
private const val SAMPLE_TOLERANCE = 12

// allow some tolerance for how long an ANR interval lasts
private const val INTERVAL_DURATION_TOLERANCE = 500

// allow the SDK to initialize first
private const val SDK_INIT_TOLERANCE_MS = 1000L

// gap between intervals we trigger in the test case
private const val INTERVAL_GAP_MS = 1000L

// how long we wait for the test to complete before aborting
private const val TEST_TIMEOUT_SECS = 60L

// default config for sampling interval in ms
private const val SAMPLE_INTERVAL_MS = 100

// default threshold for creating an ANR interval
private const val ANR_THRESHOLD_MS = SAMPLE_INTERVAL_MS * 10

// maximum number of samples capture
private const val MAX_SAMPLES = 80

private data class ExpectedIntervalData(
    val intervalCode: Int,
    val sampleCode: Int,
    val expectedSamples: Int,
    val expectedMethods: List<String>
) {
    val expectedDuration = (expectedSamples * SAMPLE_INTERVAL_MS) + ANR_THRESHOLD_MS
}

private val firstInterval = ExpectedIntervalData(
    AnrInterval.CODE_DEFAULT,
    AnrSample.CODE_DEFAULT,
    100,
    listOf(
        "io.embrace.android.embracesdk.AnrIntegrationTest.sleepThreeSeconds",
        "io.embrace.android.embracesdk.AnrIntegrationTest.sleepTwoSeconds",
        "io.embrace.android.embracesdk.AnrIntegrationTest.sleepOneSecond",
        "io.embrace.android.embracesdk.AnrIntegrationTest.sleepFiveSeconds"
    )
)

private val secondInterval = ExpectedIntervalData(
    AnrInterval.CODE_SAMPLES_CLEARED,
    AnrSample.CODE_DEFAULT,
    10,
    listOf(
        "io.embrace.android.embracesdk.AnrIntegrationTest.sleepTwoSeconds"
    )
)

private val thirdInterval = ExpectedIntervalData(
    AnrInterval.CODE_DEFAULT,
    AnrSample.CODE_DEFAULT,
    20,
    listOf(
        "io.embrace.android.embracesdk.AnrIntegrationTest.sleepThreeSeconds"
    )
)

private val fourthInterval = thirdInterval.copy()
private val fifthInterval = thirdInterval.copy()
private val sixthInterval = thirdInterval.copy()

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
            assertEquals(
                "Unexpected number of intervals. $perfInfo",
                EXPECTED_INTERVALS,
                intervals.size
            )

            validateInterval(0, intervals, firstInterval)
            validateInterval(1, intervals, secondInterval)
            validateInterval(2, intervals, thirdInterval)
            validateInterval(3, intervals, fourthInterval)
            validateInterval(4, intervals, fifthInterval)
            validateInterval(5, intervals, sixthInterval)
        }
    }

    private fun validateInterval(
        index: Int,
        intervals: List<AnrInterval>,
        data: ExpectedIntervalData
    ) {
        val interval = intervals[index]
        val errMsg: String by lazy {
            val type = Types.newParameterizedType(List::class.java, AnrInterval::class.java)
            "Assertion failed for interval $index. ${serializer.toJson(intervals, type)}"
        }

        // validate interval code
        assertEquals(errMsg, data.intervalCode, interval.code)

        // validate interval type
        assertEquals(errMsg, AnrInterval.Type.UI, interval.type)

        // validate interval lastKnownTime is null
        assertNull(errMsg, interval.lastKnownTime)

        // validate the duration (calculated via startTime/endTime) is around what we'd expect
        val duration = interval.duration()
        assertWithTolerance(
            errMsg,
            data.expectedDuration,
            duration.toInt(),
            INTERVAL_DURATION_TOLERANCE
        )

        if (interval.code != AnrInterval.CODE_SAMPLES_CLEARED) {
            validateSamples(interval, index, errMsg, data)
        }
    }

    private fun validateSamples(
        interval: AnrInterval,
        index: Int,
        errMsg: String,
        data: ExpectedIntervalData
    ) {
        // validate there was roughly 1 sample every 100ms
        val samples = checkNotNull(interval.anrSampleList?.samples) {
            "Interval $index was missing samples completely. $errMsg\ninterval=${
                serializer.toJson(
                    interval
                )
            }"
        }
        assertWithTolerance(errMsg, data.expectedSamples, samples.size, SAMPLE_TOLERANCE)

        // validate the samples all recorded their overhead
        assertTrue(errMsg, samples.all { checkNotNull(it.sampleOverheadMs) >= 0 })

        // validate that all timestamps are ascending
        assertTrue(errMsg, samples == samples.sortedBy(AnrSample::timestamp))

        // validate the samples have the expected code
        if (data.expectedSamples <= MAX_SAMPLES) {
            assertTrue(errMsg, samples.all { it.code == data.sampleCode })
        } else {
            val withStacktraces = samples.count { it.code == data.sampleCode }
            val withoutStacktraces =
                samples.count { it.code == AnrSample.CODE_SAMPLE_LIMIT_REACHED }

            assertWithTolerance(errMsg, MAX_SAMPLES, withStacktraces, SAMPLE_TOLERANCE)
            assertWithTolerance(
                errMsg,
                data.expectedSamples - MAX_SAMPLES,
                withoutStacktraces,
                SAMPLE_TOLERANCE
            )
            assertTrue(
                errMsg,
                samples.filter { it.code == AnrSample.CODE_SAMPLE_LIMIT_REACHED }
                    .all { it.threads == null })
        }

        // validate that threads contains the method names in the expected order
        val threads: List<List<ThreadInfo>> = samples.mapNotNull(AnrSample::threads)
        val nonEmptyThreads: List<List<String>> = threads
            .filter(List<ThreadInfo>::isNotEmpty)
            .flatten()
            .map { checkNotNull(it.lines) }
        assertTrue(errMsg, nonEmptyThreads.size >= data.expectedMethods.size)

        data.expectedMethods.forEachIndexed { k, method ->
            assertEquals(
                errMsg,
                1,
                nonEmptyThreads[k].count { it.startsWith(method) })
        }
    }

    private fun startAnrIntervals() {
        handler.postDelayed(Runnable {
            Log.i("Embrace", "Starting first ANR interval")
            sleepThreeSeconds()
            sleepTwoSeconds()
            sleepOneSecond()
            sleepFiveSeconds()
            latch.countDown()
            scheduleNextMainThreadWork { produceSecondAnrInterval() }
        }, SDK_INIT_TOLERANCE_MS)
    }

    private fun produceSecondAnrInterval() {
        Log.i("Embrace", "Starting second ANR interval")
        sleepTwoSeconds()
        latch.countDown()
        scheduleNextMainThreadWork { produceThirdAnrInterval() }
    }

    private fun produceThirdAnrInterval() {
        Log.i("Embrace", "Starting third ANR interval")
        sleepThreeSeconds()
        latch.countDown()
        scheduleNextMainThreadWork { produceFourthAnrInterval() }
    }

    private fun produceFourthAnrInterval() {
        Log.i("Embrace", "Starting fourth ANR interval")
        sleepThreeSeconds()
        latch.countDown()
        scheduleNextMainThreadWork { produceFifthAnrInterval() }
    }

    private fun produceFifthAnrInterval() {
        Log.i("Embrace", "Starting fifth ANR interval")
        sleepThreeSeconds()
        latch.countDown()
        scheduleNextMainThreadWork { produceSixthAnrInterval() }
    }

    private fun produceSixthAnrInterval() {
        Log.i("Embrace", "Starting sixth ANR interval")
        sleepThreeSeconds()
        latch.countDown()
    }

    private fun scheduleNextMainThreadWork(action: () -> Unit) {
        handler.looper.queue.addIdleHandler {
            handler.postDelayed(action, INTERVAL_GAP_MS)
            false
        }
    }

    private fun sleepOneSecond() = Thread.sleep(1000)
    private fun sleepTwoSeconds() = Thread.sleep(2000)
    private fun sleepThreeSeconds() = Thread.sleep(3000)
    private fun sleepFiveSeconds() = Thread.sleep(5000)

    private fun assertWithTolerance(msg: String, expected: Int, observed: Int, tolerance: Int) {
        val abs = kotlin.math.abs(expected - observed)
        assertTrue("Expected $expected but got $observed. $msg", abs < tolerance)
    }
}
