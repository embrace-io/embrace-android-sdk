package io.embrace.android.embracesdk.anr

import io.embrace.android.embracesdk.fakes.FakeAnrService
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.payload.AnrInterval
import io.embrace.android.embracesdk.payload.AnrSample
import io.embrace.android.embracesdk.payload.AnrSampleList
import io.embrace.android.embracesdk.payload.ThreadInfo
import io.embrace.android.embracesdk.payload.extensions.clearSamples
import io.opentelemetry.api.trace.SpanId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    private lateinit var mapper: AnrOtelMapper

    private val stacktrace = ThreadInfo(
        threadId = 1,
        state = Thread.State.BLOCKED,
        name = "main",
        priority = 5,
        lines = listOf(
            "com.example.app.MainActivity.onCreate(MainActivity.kt:10)",
            "com.example.app.MainActivity.onCreate(MainActivity.kt:20)"
        )
    )

    private val threads = listOf(stacktrace)

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

    private val completedInterval = AnrInterval(
        startTime = START_TIME_MS,
        endTime = END_TIME_MS,
        code = AnrInterval.CODE_DEFAULT,
        anrSampleList = AnrSampleList(listOf(firstSample, secondSample))
    )

    private val inProgressInterval =
        completedInterval.copy(endTime = null, lastKnownTime = LAST_KNOWN_TIME)

    private val clearedInterval = completedInterval.clearSamples()

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
        mapper = AnrOtelMapper(anrService)
    }

    @Test
    fun `empty intervals`() {
        assertEquals(emptyList<Span>(), mapper.snapshot())
    }

    @Test
    fun `map multiple intervals to otel`() {
        anrService.data = listOf(
            completedInterval,
            inProgressInterval,
            clearedInterval,
            intervalWithLimitedSample
        )
        val spans = mapper.snapshot()
        assertEquals(4, spans.size)
    }

    @Test
    fun `map completed interval`() {
        anrService.data = listOf(completedInterval)
        val spans = mapper.snapshot()
        val span = spans.single()
        span.assertCommonOtelCharacteristics()
        assertEquals(END_TIME_MS, span.endTimeUnixNano?.nanosToMillis())
        assertEquals("0", span.attributes?.findAttribute("emb.interval_code")?.data)

        // validate samples
        val events = checkNotNull(span.events)
        assertEquals(2, events.size)
        assertSampleMapped(events[0], firstSample)
        assertSampleMapped(events[1], secondSample)
    }

    @Test
    fun `map in progress interval`() {
        anrService.data = listOf(inProgressInterval)
        val spans = mapper.snapshot()
        val span = spans.single()
        span.assertCommonOtelCharacteristics()

        assertNull(span.endTimeUnixNano)
        val attributes = checkNotNull(span.attributes)
        val lastKnownTime =
            checkNotNull(attributes.findAttribute("emb.last_known_time_unix_nano").data)
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
        val spans = mapper.snapshot()
        val span = spans.single()
        span.assertCommonOtelCharacteristics()
        assertEquals("1", span.attributes?.findAttribute("emb.interval_code")?.data)
        assertEquals(0, span.events?.size)
    }

    @Test
    fun `map limited sample`() {
        anrService.data = listOf(intervalWithLimitedSample)
        val spans = mapper.snapshot()
        val span = spans.single()
        span.assertCommonOtelCharacteristics()
        assertEquals("0", span.attributes?.findAttribute("emb.interval_code")?.data)

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

    private fun Span.assertCommonOtelCharacteristics() {
        assertNotNull(traceId)
        assertNotNull(spanId)
        assertEquals(SpanId.getInvalid(), parentSpanId)
        assertEquals("emb-thread-blockage", name)
        assertEquals(START_TIME_MS, startTimeUnixNano?.nanosToMillis())
        assertEquals(Span.Status.OK, status)
    }

    private fun assertSampleMapped(event: SpanEvent, sample: AnrSample) {
        assertEquals("perf.thread_blockage_sample", event.name)
        assertEquals(sample.timestamp, checkNotNull(event.timeUnixNano).nanosToMillis())

        val attrs = checkNotNull(event.attributes)
        val overhead = attrs.findAttribute("emb.sample_overhead").data
        assertEquals(sample.sampleOverheadMs, overhead?.toLong()?.nanosToMillis())

        assertEquals(sample.code, attrs.findAttribute("emb.sample_code").data?.toInt())

        // validate threads
        val thread = checkNotNull(sample.threads?.single())
        assertEquals(thread.state.toString(), attrs.findAttribute("emb.thread_state").data)
        assertEquals(thread.priority, attrs.findAttribute("emb.thread_priority").data?.toInt())
        assertEquals(thread.lines?.size, attrs.findAttribute("emb.frame_count").data?.toInt())
        assertEquals(thread.lines?.joinToString("\n"), attrs.findAttribute("emb.stacktrace").data)
    }

    private fun List<Attribute>.findAttribute(key: String): Attribute {
        return single { it.key == key }
    }
}
