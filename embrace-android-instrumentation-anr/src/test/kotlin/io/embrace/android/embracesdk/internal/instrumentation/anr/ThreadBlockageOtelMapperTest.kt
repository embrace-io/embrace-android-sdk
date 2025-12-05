package io.embrace.android.embracesdk.internal.instrumentation.anr

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.arch.stacktrace.ThreadSample
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.instrumentation.anr.payload.ThreadBlockageInterval
import io.embrace.android.embracesdk.internal.instrumentation.anr.payload.ThreadBlockageSample
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.opentelemetry.kotlin.semconv.ExceptionAttributes
import io.embrace.opentelemetry.kotlin.semconv.JvmAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

private const val END_TIME_MS = 2000L
private const val START_TIME_MS = 1000L
private const val LAST_KNOWN_TIME = 3000L
private const val FIRST_SAMPLE_MS: Long = 15000000L
private const val SECOND_SAMPLE_MS: Long = 15000100L
private const val FIRST_SAMPLE_OVERHEAD_MS = 3L
private const val SECOND_SAMPLE_OVERHEAD_MS = 5L

@RunWith(AndroidJUnit4::class)
internal class ThreadBlockageOtelMapperTest {

    private val threadSample = ThreadSample(
        threadId = 1,
        state = Thread.State.BLOCKED,
        name = "main",
        priority = 5,
        lines = listOf(
            "com.example.app.MainActivity.onCreate(MainActivity.kt:10)",
            "com.example.app.MainActivity.onCreate(MainActivity.kt:20)"
        ),
        frameCount = 2
    )
    private val truncatedThreadSample = ThreadSample(
        threadId = 1,
        state = Thread.State.BLOCKED,
        name = "main",
        priority = 5,
        lines = listOf(
            "com.example.app.MainActivity.onCreate(MainActivity.kt:10)",
            "com.example.app.MainActivity.onCreate(MainActivity.kt:20)"
        ),
        frameCount = 10000
    )

    private val firstSample = ThreadBlockageSample(
        timestamp = FIRST_SAMPLE_MS,
        sampleOverheadMs = FIRST_SAMPLE_OVERHEAD_MS,
        threadSample = threadSample
    )

    private val secondSample = ThreadBlockageSample(
        timestamp = SECOND_SAMPLE_MS,
        sampleOverheadMs = SECOND_SAMPLE_OVERHEAD_MS,
        threadSample = threadSample
    )

    private val truncatedSecondSample = ThreadBlockageSample(
        timestamp = SECOND_SAMPLE_MS,
        sampleOverheadMs = SECOND_SAMPLE_OVERHEAD_MS,
        threadSample = truncatedThreadSample
    )

    private val completedInterval = ThreadBlockageInterval(
        startTime = START_TIME_MS,
        endTime = END_TIME_MS,
        samples = listOf(firstSample, secondSample)
    )

    private val completedIntervalWithTruncatedSample = ThreadBlockageInterval(
        startTime = START_TIME_MS,
        endTime = END_TIME_MS,
        samples = listOf(firstSample, truncatedSecondSample)
    )

    private val inProgressInterval =
        completedInterval.copy(endTime = null, lastKnownTime = LAST_KNOWN_TIME)

    private val clearedInterval = completedInterval.clearSamples()

    private lateinit var clock: FakeClock
    private val random = Random(0)

    private val intervalWithLimitedSample = completedInterval.copy(
        samples = List(100) { k ->
            if (k >= 80) {
                firstSample.copy(code = ThreadBlockageSample.CODE_SAMPLE_LIMIT_REACHED)
            } else {
                firstSample
            }
        }
    )

    @Before
    fun setUp() {
        clock = FakeClock()
    }

    @Test
    fun `map completed interval`() {
        val span = listOf(completedInterval).map { mapIntervalToSpan(it, clock, random) }.single()
        span.assertCommonOtelCharacteristics()
        assertEquals(END_TIME_MS, span.endTimeNanos?.nanosToMillis())
        assertEquals("0", span.attributes?.findAttribute("interval_code")?.data)

        // validate samples
        val events = checkNotNull(span.events)
        assertEquals(2, events.size)
        assertSampleMapped(events[0], firstSample)
        assertSampleMapped(events[1], secondSample)
    }

    @Test
    fun `map in progress interval`() {
        val span = listOf(inProgressInterval).map { mapIntervalToSpan(it, clock, random) }.single()
        span.assertCommonOtelCharacteristics()

        assertEquals(clock.now().millisToNanos(), span.endTimeNanos)
        val attributes = checkNotNull(span.attributes)
        val lastKnownTime =
            checkNotNull(attributes.findAttribute("last_known_time_unix_nano").data)
        assertEquals(LAST_KNOWN_TIME, lastKnownTime.toLong().nanosToMillis())

        // validate samples
        val events = checkNotNull(span.events)
        assertEquals(2, events.size)
        assertSampleMapped(events[0], firstSample)
        assertSampleMapped(events[1], secondSample)
    }

    @Test
    fun `map cleared interval`() {
        val span = listOf(clearedInterval).map { mapIntervalToSpan(it, clock, random) }.single()
        span.assertCommonOtelCharacteristics()
        assertEquals("1", span.attributes?.findAttribute("interval_code")?.data)
        assertEquals(0, span.events?.size)
    }

    @Test
    fun `map limited sample`() {
        val span = listOf(intervalWithLimitedSample).map { mapIntervalToSpan(it, clock, random) }.single()
        span.assertCommonOtelCharacteristics()
        assertEquals("0", span.attributes?.findAttribute("interval_code")?.data)

        // validate samples
        val events = checkNotNull(span.events)
        assertEquals(100, events.size)
        events.forEachIndexed { index, event ->
            if (index >= 80) {
                assertSampleMapped(event, firstSample.copy(code = ThreadBlockageSample.CODE_SAMPLE_LIMIT_REACHED))
            } else {
                assertSampleMapped(event, firstSample)
            }
        }
    }

    @Test
    fun `truncated stack shows the pre-truncated frame count`() {
        val span = listOf(completedIntervalWithTruncatedSample).map { mapIntervalToSpan(it, clock, random) }.single()
        val events = checkNotNull(span.events)
        assertEquals(2, events.size)
        assertSampleMapped(events[1], truncatedSecondSample)
    }

    private fun Span.assertCommonOtelCharacteristics() {
        assertNotNull(traceId)
        assertNotNull(spanId)
        assertEquals("0000000000000000", parentSpanId)
        assertEquals("emb-thread-blockage", name)
        assertEquals(START_TIME_MS, startTimeNanos?.nanosToMillis())
        assertNotEquals(Span.Status.ERROR, status)
        assertEquals("perf.thread_blockage", attributes?.findAttribute("emb.type")?.data)
    }

    private fun assertSampleMapped(event: SpanEvent, sample: ThreadBlockageSample) {
        assertEquals("perf.thread_blockage_sample", event.name)
        assertEquals("perf.thread_blockage_sample", event.attributes?.findAttribute("emb.type")?.data)
        assertEquals(sample.timestamp, checkNotNull(event.timestampNanos).nanosToMillis())

        val attrs = checkNotNull(event.attributes)
        val overhead = attrs.findAttribute("sample_overhead").data
        assertEquals(sample.sampleOverheadMs, overhead?.toLong()?.nanosToMillis())

        assertEquals(sample.code, attrs.findAttribute("sample_code").data?.toInt())

        // validate threads
        val thread = checkNotNull(sample.threadSample)
        assertEquals(thread.state.toString(), attrs.findAttribute(JvmAttributes.JVM_THREAD_STATE).data)
        assertEquals(thread.priority, attrs.findAttribute("thread_priority").data?.toInt())
        assertEquals(thread.frameCount, attrs.findAttribute("frame_count").data?.toInt())
        assertEquals(
            thread.lines?.joinToString("\n"),
            attrs.findAttribute(ExceptionAttributes.EXCEPTION_STACKTRACE).data
        )
    }

    private fun List<Attribute>.findAttribute(key: String): Attribute {
        return single { it.key == key }
    }
}
