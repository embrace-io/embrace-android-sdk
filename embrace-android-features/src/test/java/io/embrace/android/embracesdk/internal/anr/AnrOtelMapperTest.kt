package io.embrace.android.embracesdk.internal.anr

import io.embrace.android.embracesdk.arch.assertSuccessful
import io.embrace.android.embracesdk.fakes.FakeAnrService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.sdk.id.OtelIds
import io.embrace.android.embracesdk.internal.payload.AnrInterval
import io.embrace.android.embracesdk.internal.payload.AnrSample
import io.embrace.android.embracesdk.internal.payload.AnrSampleList
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.payload.ThreadInfo
import io.opentelemetry.semconv.ExceptionAttributes
import io.opentelemetry.semconv.JvmAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

private const val END_TIME_MS = 2000L
private const val START_TIME_MS = 1000L
private const val LAST_KNOWN_TIME = 3000L
private const val FIRST_SAMPLE_MS: Long = 15000000L
private const val SECOND_SAMPLE_MS: Long = 15000100L
private const val FIRST_SAMPLE_OVERHEAD_MS = 3L
private const val SECOND_SAMPLE_OVERHEAD_MS = 5L

internal class AnrOtelMapperTest {

    private lateinit var anrService: FakeAnrService
    private lateinit var spanService: FakeSpanService
    private lateinit var mapper: AnrOtelMapper

    private val stacktrace = ThreadInfo(
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

    private val threads = listOf(stacktrace)
    private val truncatedStack = stacktrace.copy(frameCount = 10000)
    private val truncatedThreads = listOf(truncatedStack)

    private val firstSample = AnrSample(
        timestamp = FIRST_SAMPLE_MS,
        sampleOverheadMs = FIRST_SAMPLE_OVERHEAD_MS,
        code = AnrSample.CODE_DEFAULT,
        threads = threads
    )

    private val secondSample = AnrSample(
        timestamp = SECOND_SAMPLE_MS,
        sampleOverheadMs = SECOND_SAMPLE_OVERHEAD_MS,
        code = AnrSample.CODE_DEFAULT,
        threads = threads
    )

    private val truncatedSecondSample = AnrSample(
        timestamp = SECOND_SAMPLE_MS,
        sampleOverheadMs = SECOND_SAMPLE_OVERHEAD_MS,
        code = AnrSample.CODE_DEFAULT,
        threads = truncatedThreads
    )

    private val completedInterval = AnrInterval(
        startTime = START_TIME_MS,
        endTime = END_TIME_MS,
        code = AnrInterval.CODE_DEFAULT,
        anrSampleList = AnrSampleList(listOf(firstSample, secondSample))
    )

    private val completedIntervalWithTruncatedSample = AnrInterval(
        startTime = START_TIME_MS,
        endTime = END_TIME_MS,
        code = AnrInterval.CODE_DEFAULT,
        anrSampleList = AnrSampleList(listOf(firstSample, truncatedSecondSample))
    )

    private val inProgressInterval =
        completedInterval.copy(endTime = null, lastKnownTime = LAST_KNOWN_TIME)

    private val clearedInterval = completedInterval.clearSamples()

    private lateinit var clock: FakeClock

    private val intervalWithLimitedSample = completedInterval.copy(
        anrSampleList = AnrSampleList(
            List(100) { k ->
                if (k >= 80) {
                    firstSample.copy(code = AnrSample.CODE_SAMPLE_LIMIT_REACHED)
                } else {
                    firstSample
                }
            }
        )
    )

    @Before
    fun setUp() {
        anrService = FakeAnrService()
        spanService = FakeSpanService()
        clock = FakeClock()
        mapper = AnrOtelMapper(anrService, clock, spanService)
    }

    @Test
    fun `empty intervals`() {
        mapper.snapshot().verifySnapshotPayload(0)
    }

    @Test
    fun `map multiple intervals to otel`() {
        anrService.data = listOf(
            completedInterval,
            inProgressInterval,
            clearedInterval,
            intervalWithLimitedSample
        )
        mapper.snapshot().verifySnapshotPayload(4)
        mapper.record()
        assertEquals(4, spanService.createdSpans.size)
    }

    @Test
    fun `map completed interval`() {
        anrService.data = listOf(completedInterval)
        val span = mapper.snapshot().verifySingleAnrSnapshot()
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
        anrService.data = listOf(inProgressInterval)
        val span = mapper.snapshot().verifySingleAnrSnapshot()
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
        anrService.data = listOf(clearedInterval)
        val span = mapper.snapshot().verifySingleAnrSnapshot()
        span.assertCommonOtelCharacteristics()
        assertEquals("1", span.attributes?.findAttribute("interval_code")?.data)
        assertEquals(0, span.events?.size)
    }

    @Test
    fun `map limited sample`() {
        anrService.data = listOf(intervalWithLimitedSample)
        val span = mapper.snapshot().verifySingleAnrSnapshot()
        span.assertCommonOtelCharacteristics()
        assertEquals("0", span.attributes?.findAttribute("interval_code")?.data)

        // validate samples
        val events = checkNotNull(span.events)
        assertEquals(100, events.size)
        events.forEachIndexed { index, event ->
            if (index >= 80) {
                assertSampleMapped(event, firstSample.copy(code = AnrSample.CODE_SAMPLE_LIMIT_REACHED))
            } else {
                assertSampleMapped(event, firstSample)
            }
        }
    }

    @Test
    fun `truncated stack shows the pre-truncated frame count`() {
        anrService.data = listOf(completedIntervalWithTruncatedSample)
        val span = mapper.snapshot().verifySingleAnrSnapshot()
        val events = checkNotNull(span.events)
        assertEquals(2, events.size)
        assertSampleMapped(events[1], truncatedSecondSample)
    }

    private fun List<Span>.verifySingleAnrSnapshot(): Span {
        verifySnapshotPayload(1)
        return single()
    }

    private fun List<Span>.verifySnapshotPayload(expectedCount: Int) {
        assertEquals(expectedCount, size)
        assertEquals(0, spanService.createdSpans.size)
    }

    private fun Span.assertCommonOtelCharacteristics() {
        assertNotNull(traceId)
        assertNotNull(spanId)
        assertEquals(OtelIds.invalidSpanId, parentSpanId)
        assertEquals("emb-thread-blockage", name)
        assertEquals(START_TIME_MS, startTimeNanos?.nanosToMillis())
        assertSuccessful()
        assertEquals("perf.thread_blockage", attributes?.findAttribute("emb.type")?.data)
    }

    private fun assertSampleMapped(event: SpanEvent, sample: AnrSample) {
        assertEquals("perf.thread_blockage_sample", event.name)
        assertEquals("perf.thread_blockage_sample", event.attributes?.findAttribute("emb.type")?.data)
        assertEquals(sample.timestamp, checkNotNull(event.timestampNanos).nanosToMillis())

        val attrs = checkNotNull(event.attributes)
        val overhead = attrs.findAttribute("sample_overhead").data
        assertEquals(sample.sampleOverheadMs, overhead?.toLong()?.nanosToMillis())

        assertEquals(sample.code, attrs.findAttribute("sample_code").data?.toInt())

        // validate threads
        val thread = checkNotNull(sample.threads?.single())
        assertEquals(thread.state.toString(), attrs.findAttribute(JvmAttributes.JVM_THREAD_STATE.key).data)
        assertEquals(thread.priority, attrs.findAttribute("thread_priority").data?.toInt())
        assertEquals(thread.frameCount, attrs.findAttribute("frame_count").data?.toInt())
        assertEquals(
            thread.lines?.joinToString("\n"),
            attrs.findAttribute(ExceptionAttributes.EXCEPTION_STACKTRACE.key).data
        )
    }

    private fun List<Attribute>.findAttribute(key: String): Attribute {
        return single { it.key == key }
    }
}
