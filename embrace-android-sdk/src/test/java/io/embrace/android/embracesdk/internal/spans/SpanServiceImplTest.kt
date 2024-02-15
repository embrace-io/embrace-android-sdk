package io.embrace.android.embracesdk.internal.spans

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fixtures.MAX_LENGTH_SPAN_NAME
import io.embrace.android.embracesdk.fixtures.TOO_LONG_ATTRIBUTE_KEY
import io.embrace.android.embracesdk.fixtures.TOO_LONG_ATTRIBUTE_VALUE
import io.embrace.android.embracesdk.fixtures.TOO_LONG_SPAN_NAME
import io.embrace.android.embracesdk.fixtures.maxSizeAttributes
import io.embrace.android.embracesdk.fixtures.maxSizeEvents
import io.embrace.android.embracesdk.fixtures.tooBigAttributes
import io.embrace.android.embracesdk.fixtures.tooBigEvents
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.trace.SpanId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SpanServiceImplTest {
    private lateinit var spanSink: SpanSink
    private lateinit var currentSessionSpan: CurrentSessionSpan
    private lateinit var spansService: SpanServiceImpl
    private val clock = FakeClock(1000L)

    @Before
    fun setup() {
        val initModule = FakeInitModule(clock = clock)
        spanSink = initModule.openTelemetryModule.spanSink
        currentSessionSpan = initModule.openTelemetryModule.currentSessionSpan
        spansService = SpanServiceImpl(
            spanRepository = initModule.openTelemetryModule.spanRepository,
            currentSessionSpan = currentSessionSpan,
            tracer = initModule.openTelemetryModule.tracer
        )
        spansService.initializeService(clock.nowInNanos())
    }

    @Test
    fun `create trace with default parameters`() {
        val embraceSpan = checkNotNull(spansService.createSpan(name = "test-span"))
        assertNull(embraceSpan.parent)
        assertTrue(embraceSpan.start())
        assertTrue(embraceSpan.stop())
        with(verifyAndReturnSoleCompletedSpan("emb-test-span")) {
            assertEquals(SpanId.getInvalid(), parentSpanId)
            assertEquals(
                EmbraceAttributes.Type.PERFORMANCE.name,
                attributes[EmbraceAttributes.Type.PERFORMANCE.keyName()]
            )
            assertTrue(isKey())
        }
    }

    @Test
    fun `create trace with custom type`() {
        val embraceSpan = checkNotNull(
            spansService.createSpan(
                name = "test-span",
                type = EmbraceAttributes.Type.PERFORMANCE
            )
        )
        assertTrue(embraceSpan.start())
        assertTrue(embraceSpan.stop())
        with(verifyAndReturnSoleCompletedSpan("emb-test-span")) {
            assertEquals(SpanId.getInvalid(), parentSpanId)
            assertEquals(
                EmbraceAttributes.Type.PERFORMANCE.name,
                attributes[EmbraceAttributes.Type.PERFORMANCE.keyName()]
            )
            assertTrue(isKey())
        }
    }

    @Test
    fun `create trace with children`() {
        val parentSpan = spansService.createSpan(name = "test-span")
        checkNotNull(parentSpan).start()
        val childSpan = spansService.createSpan(name = "child-span", parent = parentSpan)
        checkNotNull(childSpan).start()
        assertTrue(parentSpan.traceId == childSpan.traceId)
        assertTrue(parentSpan.spanId == checkNotNull(childSpan.parent).spanId)
        assertTrue(childSpan.stop())
        assertTrue(parentSpan.stop())

        val currentSpans = spanSink.completedSpans()
        assertEquals(2, currentSpans.size)
        assertTrue(currentSpans[0].traceId == currentSpans[1].traceId)

        with(currentSpans[0]) {
            assertEquals("emb-child-span", name)
            assertEquals(childSpan.spanId, spanId)
            assertEquals(childSpan.traceId, traceId)
            assertFalse(isKey())
            assertTrue(isPrivate())
        }

        with(currentSpans[1]) {
            assertEquals("emb-test-span", name)
            assertEquals(SpanId.getInvalid(), parentSpanId)
            assertEquals(parentSpan.spanId, spanId)
            assertEquals(parentSpan.traceId, traceId)
            assertTrue(isKey())
            assertTrue(isPrivate())
        }
    }

    @Test
    fun `start span created from previous session`() {
        val embraceSpan = checkNotNull(spansService.createSpan(name = "test-span"))
        currentSessionSpan.endSession()
        assertTrue(embraceSpan.start())
    }

    @Test
    fun `cannot create span before initialization`() {
        assertNull(FakeInitModule(clock = clock).openTelemetryModule.spanService.createSpan(name = "test"))
    }

    @Test
    fun `cannot create span with blank name`() {
        assertNull(spansService.createSpan(name = ""))
        assertNull(spansService.createSpan(name = " "))
    }

    @Test
    fun `cannot create child if parent not started`() {
        val parentSpan = spansService.createSpan(name = "test-span")
        val childSpan = spansService.createSpan(name = "child-span", parent = parentSpan)
        assertNull(childSpan)
    }

    @Test
    fun `can create child if parent has stopped`() {
        val parentSpan = checkNotNull(spansService.createSpan(name = "test-span"))
        assertTrue(parentSpan.start())
        assertTrue(parentSpan.stop())
        val childSpan = spansService.createSpan(name = "child-span", parent = parentSpan)
        assertNotNull(childSpan)
    }

    @Test
    fun `record internal completed span with all the fixings`() {
        val expectedName = "test-span"
        val expectedStartTime = clock.now()
        val expectedEndTime = expectedStartTime + 100L
        val expectedType = EmbraceAttributes.Type.PERFORMANCE
        val expectedAttributes = mapOf(
            Pair("attribute1", "value1"),
            Pair("attribute2", "value2")
        )
        val expectedEvents = listOf(
            EmbraceSpanEvent(name = "event1", timestampNanos = 0L, attributes = expectedAttributes),
            EmbraceSpanEvent(name = "event2", timestampNanos = 5L, attributes = expectedAttributes)
        )

        spansService.recordCompletedSpan(
            name = expectedName,
            startTimeNanos = expectedStartTime,
            endTimeNanos = expectedEndTime,
            type = expectedType,
            attributes = expectedAttributes,
            events = expectedEvents
        )

        with(verifyAndReturnSoleCompletedSpan("emb-$expectedName")) {
            assertEquals(expectedStartTime, startTimeNanos)
            assertEquals(expectedEndTime, endTimeNanos)
            assertEquals(expectedType.name, attributes[EmbraceAttributes.Type.PERFORMANCE.keyName()])
            assertEquals(SpanId.getInvalid(), parentSpanId)
            assertTrue(isKey())
            assertTrue(isPrivate())
            expectedAttributes.forEach {
                assertEquals(it.value, attributes[it.key])
            }
            assertEquals(expectedEvents, events)
        }
    }

    @Test
    fun `record completed child span`() {
        val expectedName = "child-span"
        val expectedStartTime = clock.now()
        val expectedEndTime = expectedStartTime + 100L
        val parentSpan = checkNotNull(spansService.createSpan(name = "test-span"))
        assertTrue(parentSpan.start())
        assertTrue(
            spansService.recordCompletedSpan(
                name = expectedName,
                parent = parentSpan,
                startTimeNanos = expectedStartTime,
                endTimeNanos = expectedEndTime
            )
        )

        with(verifyAndReturnSoleCompletedSpan("emb-$expectedName")) {
            assertEquals(expectedStartTime, startTimeNanos)
            assertEquals(expectedEndTime, endTimeNanos)
            assertFalse(isKey())
            assertTrue(isPrivate())
        }
        assertTrue(parentSpan.stop())

        val currentSpans = spanSink.completedSpans()
        assertEquals(2, currentSpans.size)
        assertTrue(currentSpans[0].traceId == currentSpans[1].traceId)
        assertTrue(currentSpans[0].parentSpanId == currentSpans[1].spanId)
    }

    @Test
    fun `record completed child span with stopped parent`() {
        val expectedName = "child-span"
        val expectedStartTime = clock.now()
        val expectedEndTime = expectedStartTime + 100L
        val parentSpan = checkNotNull(spansService.createSpan(name = "test-span"))
        assertTrue(parentSpan.start())
        assertTrue(parentSpan.stop())
        currentSessionSpan.endSession()
        assertTrue(
            spansService.recordCompletedSpan(
                name = expectedName,
                parent = parentSpan,
                startTimeNanos = expectedStartTime,
                endTimeNanos = expectedEndTime
            )
        )
    }

    @Test
    fun `can't record completed child span with not-started parent`() {
        val expectedName = "child-span"
        val parentSpan = checkNotNull(spansService.createSpan(name = "test-span"))
        assertFalse(
            spansService.recordCompletedSpan(
                name = expectedName,
                parent = parentSpan,
                startTimeNanos = 10L,
                endTimeNanos = 100L
            )
        )
    }

    @Test
    fun `record spans with different ending error codes `() {
        ErrorCode.values().forEach {
            assertTrue(
                spansService.recordCompletedSpan(
                    name = "test${it.name}",
                    startTimeNanos = 0,
                    endTimeNanos = 1,
                    errorCode = it
                )
            )
            with(verifyAndReturnSoleCompletedSpan("emb-test${it.name}")) {
                assertEquals(it.name, attributes[it.keyName()])
            }
            spanSink.flushSpans()
        }
    }

    @Test
    fun `validate start and end times for a completed span`() {
        assertFalse(
            spansService.recordCompletedSpan(
                name = "test-pan",
                startTimeNanos = 500,
                endTimeNanos = 499
            )
        )
    }

    @Test
    fun `cannot record completed span if there is not current session span`() {
        currentSessionSpan.endSession(
            appTerminationCause = EmbraceAttributes.AppTerminationCause.USER_TERMINATION
        )
        assertFalse(
            spansService.recordCompletedSpan(
                name = "test-span",
                startTimeNanos = 500,
                endTimeNanos = 600
            )
        )
    }

    @Test
    fun `record lambda running as trace`() {
        val returnThis = "yooooo"
        val lambdaReturn = spansService.recordSpan(name = "test-span") {
            returnThis
        }

        assertEquals(returnThis, lambdaReturn)
        with(verifyAndReturnSoleCompletedSpan("emb-test-span")) {
            assertEquals(SpanId.getInvalid(), parentSpanId)
            assertEquals(
                EmbraceAttributes.Type.PERFORMANCE.name,
                attributes[EmbraceAttributes.Type.PERFORMANCE.keyName()]
            )
            assertTrue(isKey())
            assertTrue(isPrivate())
        }
    }

    @Test
    fun `record lambda running as a child span`() {
        val parentSpan = checkNotNull(spansService.createSpan(name = "test-span"))
        assertTrue(parentSpan.start())
        spansService.recordSpan(name = "child-span", parent = parentSpan) {
            parentSpan.hashCode()
        }

        assertTrue(parentSpan.stop())

        val currentSpans = spanSink.completedSpans()
        assertEquals(2, currentSpans.size)
        assertTrue(currentSpans[0].traceId == currentSpans[1].traceId)
        assertTrue(currentSpans[0].parentSpanId == currentSpans[1].spanId)

        with(currentSpans[0]) {
            assertEquals("emb-child-span", name)
            assertFalse(isKey())
            assertTrue(isPrivate())
        }
    }

    @Test
    fun `lambda with not-started parent will still run and return value`() {
        val parentSpan = checkNotNull(spansService.createSpan(name = "test-span"))
        val returnThis = 1
        val returnValue = spansService.recordSpan(name = "child-span", parent = parentSpan) {
            returnThis + 1
        }

        assertEquals(2, returnValue)
        assertEquals(0, spanSink.completedSpans().size)
    }

    @Test
    fun `lambda with stopped parent will still be recorded`() {
        val parentSpan = checkNotNull(spansService.createSpan(name = "test-span"))
        assertTrue(parentSpan.start())
        assertTrue(parentSpan.stop())
        val returnThis = 1
        val returnValue = spansService.recordSpan(name = "child-span", parent = parentSpan) {
            returnThis + 1
        }

        assertEquals(2, returnValue)
        assertEquals(2, spanSink.completedSpans().size)
    }

    @Test
    fun `recording span as lambda throws an exception will record a failed span and rethrows exception`() {
        assertThrows(RuntimeException::class.java) {
            spansService.recordSpan(name = "test-span") {
                throw RuntimeException("You done bad")
            }
        }

        with(verifyAndReturnSoleCompletedSpan("emb-test-span")) {
            assertEquals(
                ErrorCode.FAILURE.name,
                attributes[ErrorCode.FAILURE.keyName()]
            )
        }
    }

    @Test
    fun `recording span as lambda with no current active session will run code but not log span`() {
        currentSessionSpan.endSession(
            appTerminationCause = EmbraceAttributes.AppTerminationCause.USER_TERMINATION
        )
        var executed = false
        spansService.recordSpan(name = "test-span") {
            executed = true
        }

        assertTrue(executed)
        assertEquals(0, spanSink.completedSpans().size)
    }

    @Test
    fun `after ending session with app termination, spans cannot be recorded`() {
        currentSessionSpan.endSession(EmbraceAttributes.AppTerminationCause.USER_TERMINATION)
        spansService.recordSpan("test-span") {
            // do thing
        }
        assertFalse(spansService.recordCompletedSpan("test-span-2", startTimeNanos = 0, endTimeNanos = 1))
        assertEquals(0, spanSink.completedSpans().size)
    }

    @Test
    fun `check name length limit`() {
        assertNull(spansService.createSpan(name = TOO_LONG_SPAN_NAME))
        assertFalse(spansService.recordCompletedSpan(name = TOO_LONG_SPAN_NAME, startTimeNanos = 100L, endTimeNanos = 200L))
        assertNotNull(spansService.recordSpan(name = TOO_LONG_SPAN_NAME) { 1 })
        assertEquals(0, spanSink.completedSpans().size)
        assertNotNull(spansService.createSpan(name = MAX_LENGTH_SPAN_NAME))
        assertNotNull(spansService.recordSpan(name = MAX_LENGTH_SPAN_NAME) { 2 })
        assertTrue(spansService.recordCompletedSpan(name = MAX_LENGTH_SPAN_NAME, startTimeNanos = 100L, endTimeNanos = 200L))
        assertEquals(2, spanSink.completedSpans().size)
    }

    @Test
    fun `check events limit`() {
        assertFalse(
            spansService.recordCompletedSpan(
                name = "too many events",
                startTimeNanos = 100L,
                endTimeNanos = 200L,
                events = tooBigEvents
            )
        )
        assertTrue(
            spansService.recordCompletedSpan(
                name = MAX_LENGTH_SPAN_NAME,
                startTimeNanos = 100L,
                endTimeNanos = 200L,
                events = maxSizeEvents
            )
        )

        spanSink.flushSpans()

        val attributesMap = mutableMapOf(
            Pair(TOO_LONG_ATTRIBUTE_KEY, "value"),
            Pair("key", TOO_LONG_ATTRIBUTE_VALUE),
        )
        repeat(EmbraceSpanEvent.MAX_EVENT_ATTRIBUTE_COUNT - 2) {
            attributesMap["key$it"] = "value"
        }

        val events = mutableListOf(checkNotNull(EmbraceSpanEvent.create("event", 100L, attributesMap)))
        repeat(EmbraceSpanImpl.MAX_EVENT_COUNT - 1) {
            events.add(checkNotNull(EmbraceSpanEvent.create("event", 100L, null)))
        }
        assertTrue(
            spansService.recordCompletedSpan(
                name = MAX_LENGTH_SPAN_NAME,
                startTimeNanos = 100L,
                endTimeNanos = 200L,
                events = events
            )
        )

        val completedSpans = spanSink.completedSpans()
        assertEquals(1, completedSpans.size)
        assertEquals(10, completedSpans[0].events.size)
        assertEquals(8, completedSpans[0].events[0].attributes.size)
    }

    @Test
    fun `check attributes limit`() {
        assertFalse(
            spansService.recordCompletedSpan(
                name = "too many attributes",
                startTimeNanos = 100L,
                endTimeNanos = 200L,
                attributes = tooBigAttributes
            )
        )
        assertTrue(
            spansService.recordCompletedSpan(
                name = MAX_LENGTH_SPAN_NAME,
                startTimeNanos = 100L,
                endTimeNanos = 200L,
                attributes = maxSizeAttributes
            )
        )

        spanSink.flushSpans()

        val attributesMap = mutableMapOf(
            Pair(TOO_LONG_ATTRIBUTE_KEY, "value"),
            Pair("key", TOO_LONG_ATTRIBUTE_VALUE),
        )
        repeat(EmbraceSpanImpl.MAX_ATTRIBUTE_COUNT - 2) {
            attributesMap["key$it"] = "value"
        }

        assertTrue(
            spansService.recordCompletedSpan(
                name = MAX_LENGTH_SPAN_NAME,
                startTimeNanos = 100L,
                endTimeNanos = 200L,
                attributes = attributesMap
            )
        )

        val completedSpans = spanSink.completedSpans()
        assertEquals(1, completedSpans.size)
        assertEquals(48, completedSpans[0].attributes.filterNot { it.key.startsWith("emb.") }.size)
    }

    private fun verifyAndReturnSoleCompletedSpan(name: String): EmbraceSpanData {
        val currentSpans = spanSink.completedSpans()
        assertEquals(1, currentSpans.size)
        assertEquals(name, currentSpans[0].name)
        return currentSpans[0]
    }
}
