package io.embrace.android.embracesdk.internal.spans

import android.os.Build.VERSION_CODES.TIRAMISU
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
import io.embrace.android.embracesdk.internal.spans.SpansServiceImpl.Companion.MAX_SPAN_COUNT_PER_TRACE
import io.embrace.android.embracesdk.internal.spans.SpansServiceImpl.Companion.MAX_TRACE_COUNT_PER_SESSION
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.StatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@Config(sdk = [TIRAMISU])
@RunWith(AndroidJUnit4::class)
internal class SpansServiceImplTest {
    private lateinit var spansSink: SpansSink
    private lateinit var currentSessionSpan: CurrentSessionSpan
    private lateinit var spansService: SpansServiceImpl
    private val clock = FakeClock(1000L)

    @Test
    fun `create trace with default parameters`() {
        initAndStartNextSession()
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
        initAndStartNextSession()
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
        initAndStartNextSession()
        val parentSpan = spansService.createSpan(name = "test-span")
        checkNotNull(parentSpan).start()
        val childSpan = spansService.createSpan(name = "child-span", parent = parentSpan)
        checkNotNull(childSpan).start()
        assertTrue(parentSpan.traceId == childSpan.traceId)
        assertTrue(parentSpan.spanId == checkNotNull(childSpan.parent).spanId)
        assertTrue(childSpan.stop())
        assertTrue(parentSpan.stop())

        val currentSpans = spansSink.completedSpans()
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
        initAndStartNextSession()
        val embraceSpan = checkNotNull(spansService.createSpan(name = "test-span"))
        spansSink.flushSpans()
        assertTrue(embraceSpan.start())
    }

    @Test
    fun `cannot create span with no session span`() {
        initAndStartNextSession()
        currentSessionSpan.endSession(appTerminationCause = EmbraceAttributes.AppTerminationCause.USER_TERMINATION)
        assertNull(spansService.createSpan(name = "test"))
    }

    @Test
    fun `cannot create span with blank name`() {
        initAndStartNextSession()
        assertNull(spansService.createSpan(name = ""))
        assertNull(spansService.createSpan(name = " "))
    }

    @Test
    fun `cannot create child if parent not started`() {
        initAndStartNextSession()
        val parentSpan = spansService.createSpan(name = "test-span")
        val childSpan = spansService.createSpan(name = "child-span", parent = parentSpan)
        assertNull(childSpan)
    }

    @Test
    fun `can create child if parent has stopped`() {
        initAndStartNextSession()
        val parentSpan = checkNotNull(spansService.createSpan(name = "test-span"))
        assertTrue(parentSpan.start())
        assertTrue(parentSpan.stop())
        val childSpan = spansService.createSpan(name = "child-span", parent = parentSpan)
        assertNotNull(childSpan)
    }

    @Test
    fun `record internal completed span with all the fixings`() {
        initAndStartNextSession()
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
        initAndStartNextSession()
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

        val currentSpans = spansSink.completedSpans()
        assertEquals(2, currentSpans.size)
        assertTrue(currentSpans[0].traceId == currentSpans[1].traceId)
        assertTrue(currentSpans[0].parentSpanId == currentSpans[1].spanId)
    }

    @Test
    fun `record completed child span with stopped parent`() {
        initAndStartNextSession()
        val expectedName = "child-span"
        val expectedStartTime = clock.now()
        val expectedEndTime = expectedStartTime + 100L
        val parentSpan = checkNotNull(spansService.createSpan(name = "test-span"))
        assertTrue(parentSpan.start())
        assertTrue(parentSpan.stop())
        spansSink.flushSpans()
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
        initAndStartNextSession()
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
        initAndStartNextSession()
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
            spansSink.flushSpans()
        }
    }

    @Test
    fun `validate start and end times for a completed span`() {
        initAndStartNextSession()
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
        initAndStartNextSession()
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
        initAndStartNextSession()
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
        initAndStartNextSession()
        val parentSpan = checkNotNull(spansService.createSpan(name = "test-span"))
        assertTrue(parentSpan.start())
        spansService.recordSpan(name = "child-span", parent = parentSpan) {
            parentSpan.hashCode()
        }

        assertTrue(parentSpan.stop())

        val currentSpans = spansSink.completedSpans()
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
        initAndStartNextSession()
        val parentSpan = checkNotNull(spansService.createSpan(name = "test-span"))
        val returnThis = 1
        val returnValue = spansService.recordSpan(name = "child-span", parent = parentSpan) {
            returnThis + 1
        }

        assertEquals(2, returnValue)
        assertEquals(0, spansSink.completedSpans().size)
    }

    @Test
    fun `lambda with stopped parent will still be recorded`() {
        initAndStartNextSession()
        val parentSpan = checkNotNull(spansService.createSpan(name = "test-span"))
        assertTrue(parentSpan.start())
        assertTrue(parentSpan.stop())
        val returnThis = 1
        val returnValue = spansService.recordSpan(name = "child-span", parent = parentSpan) {
            returnThis + 1
        }

        assertEquals(2, returnValue)
        assertEquals(2, spansSink.completedSpans().size)
    }

    @Test
    fun `recording span as lambda throws an exception will record a failed span and rethrows exception`() {
        initAndStartNextSession()
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
    fun `logging span as lambda with no current active session will run code but not log span`() {
        initAndStartNextSession()
        currentSessionSpan.endSession(
            appTerminationCause = EmbraceAttributes.AppTerminationCause.USER_TERMINATION
        )
        var executed = false
        spansService.recordSpan(name = "test-span") {
            executed = true
        }

        assertTrue(executed)
        assertEquals(0, spansSink.completedSpans().size)
    }

    @Test
    fun `check name length limit`() {
        initAndStartNextSession()
        assertNull(spansService.createSpan(name = TOO_LONG_SPAN_NAME))
        assertFalse(spansService.recordCompletedSpan(name = TOO_LONG_SPAN_NAME, startTimeNanos = 100L, endTimeNanos = 200L))
        assertNotNull(spansService.recordSpan(name = TOO_LONG_SPAN_NAME) { 1 })
        assertEquals(0, spansSink.completedSpans().size)
        assertNotNull(spansService.createSpan(name = MAX_LENGTH_SPAN_NAME))
        assertNotNull(spansService.recordSpan(name = MAX_LENGTH_SPAN_NAME) { 2 })
        assertTrue(spansService.recordCompletedSpan(name = MAX_LENGTH_SPAN_NAME, startTimeNanos = 100L, endTimeNanos = 200L))
        assertEquals(2, spansSink.completedSpans().size)
    }

    @Test
    fun `check events limit`() {
        initAndStartNextSession()
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

        spansSink.flushSpans()

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

        val completedSpans = spansSink.completedSpans()
        assertEquals(1, completedSpans.size)
        assertEquals(10, completedSpans[0].events.size)
        assertEquals(8, completedSpans[0].events[0].attributes.size)
    }

    @Test
    fun `check attributes limit`() {
        initAndStartNextSession()
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

        spansSink.flushSpans()

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

        val completedSpans = spansSink.completedSpans()
        assertEquals(1, completedSpans.size)
        assertEquals(48, completedSpans[0].attributes.filterNot { it.key.startsWith("emb.") }.size)
    }

    @Test
    fun `check trace limits with maximum not started traces`() {
        initAndStartNextSession()
        repeat(MAX_TRACE_COUNT_PER_SESSION) {
            assertNotNull(spansService.createSpan(name = "spanzzz$it", internal = false))
        }
        assertNull(spansService.createSpan(name = "failed-span", internal = false))
    }

    @Test
    fun `check trace limits with maximum traces recorded around a lambda`() {
        initAndStartNextSession()
        repeat(MAX_TRACE_COUNT_PER_SESSION) {
            assertEquals("derp", spansService.recordSpan(name = "record$it", internal = false) { "derp" })
        }
        assertNull(spansService.createSpan(name = "failed-span", internal = false))
    }

    @Test
    fun `check trace limits with maximum completed traces`() {
        initAndStartNextSession()
        repeat(MAX_TRACE_COUNT_PER_SESSION) {
            assertTrue(
                spansService.recordCompletedSpan(
                    name = "complete$it",
                    startTimeNanos = 100L,
                    endTimeNanos = 200L,
                    internal = false
                )
            )
        }
        assertNull(spansService.createSpan(name = "failed-span", internal = false))
    }

    @Test
    fun `check internal traces and child spans don't count towards limit`() {
        initAndStartNextSession()
        val parent = checkNotNull(spansService.createSpan(name = "test-span", internal = false))
        assertTrue(parent.start())
        assertNotNull(spansService.createSpan(name = "child-span", parent = parent, internal = false))
        assertNotNull(spansService.createSpan(name = "internal-span", parent = parent, internal = true))
        repeat(MAX_TRACE_COUNT_PER_SESSION - 1) {
            assertNotNull(spansService.createSpan(name = "spanzzz$it", internal = false))
        }
        assertNull(spansService.createSpan(name = "failed-span", internal = false))
        assertNotNull(spansService.createSpan(name = "child-span", parent = parent, internal = false))
        assertNotNull(spansService.createSpan(name = "internal-again", internal = true))
    }

    @Test
    fun `check child span per trace limit`() {
        initAndStartNextSession()
        var parentSpan: EmbraceSpan? = null
        repeat(MAX_SPAN_COUNT_PER_TRACE) {
            val span = spansService.createSpan(name = "spanzzz$it", parent = parentSpan, internal = false)
            assertTrue(checkNotNull(span).start())
            parentSpan = span
        }
        assertNull(spansService.createSpan(name = "failed-span", parent = parentSpan, internal = false))
        assertFalse(
            spansService.recordCompletedSpan(
                name = "failed-span",
                startTimeNanos = 100L,
                endTimeNanos = 200L,
                parent = parentSpan,
                internal = false
            )
        )
        spansSink.flushSpans()
        assertEquals(2, spansService.recordSpan(name = "failed-span", parent = parentSpan, internal = false) { 2 })
        assertEquals(0, spansSink.completedSpans().size)
    }

    @Test
    fun `check internal child spans don't count towards limit`() {
        initAndStartNextSession()
        val parentSpan = checkNotNull(spansService.createSpan(name = "parent-span", internal = true))
        assertTrue(parentSpan.start())
        assertNotNull(spansService.createSpan(name = "failed-span", parent = parentSpan, internal = true))
        assertNotNull(spansService.recordSpan(name = "failed-span", parent = parentSpan, internal = true) { })
        assertTrue(
            spansService.recordCompletedSpan(
                name = "failed-span",
                startTimeNanos = 100L,
                endTimeNanos = 200L,
                parent = parentSpan,
                internal = true
            )
        )

        repeat(MAX_SPAN_COUNT_PER_TRACE - 1) {
            assertNotNull(spansService.createSpan(name = "spanzzz$it", parent = parentSpan, internal = false))
        }
        assertNull(spansService.createSpan(name = "failed-span", parent = parentSpan, internal = false))
        assertNotNull(spansService.createSpan(name = "internal-span", parent = parentSpan, internal = true))
    }

    @Test
    fun `flushing clears completed spans and current session span`() {
        initAndStartNextSession()
        repeat(3) {
            spansService.recordSpan("test$it") { }
        }
        assertEquals(3, spansSink.completedSpans().size)

        val flushedSpans = currentSessionSpan.endSession()
        assertEquals(4, flushedSpans.size)

        val lastFlushedSpan = flushedSpans[3]
        with(lastFlushedSpan) {
            assertEquals("emb-session-span", name)
            assertEquals(
                EmbraceAttributes.Type.SESSION.name,
                attributes[EmbraceAttributes.Type.SESSION.keyName()]
            )
            assertFalse(isKey())
            assertEquals(StatusCode.OK, status)
        }

        assertEquals(0, spansSink.completedSpans().size)
    }

    @Test
    fun `flushing with app termination and termination reason flushes session span with right termination type`() {
        EmbraceAttributes.AppTerminationCause.values().forEach {
            initAndStartNextSession()
            val flushedSpans = currentSessionSpan.endSession(it)
            assertEquals(1, flushedSpans.size)

            val lastFlushedSpan = flushedSpans[0]
            with(lastFlushedSpan) {
                assertEquals("emb-session-span", name)
                assertEquals(
                    EmbraceAttributes.Type.SESSION.name,
                    attributes[EmbraceAttributes.Type.SESSION.keyName()]
                )
                assertEquals(StatusCode.OK, status)
                assertFalse(isKey())
                assertEquals(it.name, attributes[it.keyName()])
            }

            assertEquals(0, spansSink.completedSpans().size)
        }
    }

    @Test
    fun `after flushing with app termination, spans cannot be recorded`() {
        initService()
        currentSessionSpan.endSession(EmbraceAttributes.AppTerminationCause.USER_TERMINATION)
        spansService.recordSpan("test-span") {
            // do thing
        }
        assertFalse(spansService.recordCompletedSpan("test-span-2", startTimeNanos = 0, endTimeNanos = 1))
        assertEquals(0, spansSink.completedSpans().size)
    }

    @Test
    fun `get same EmbraceSpan using spanId`() {
        initAndStartNextSession()
        val embraceSpan = checkNotNull(spansService.createSpan(name = "test-span"))
        assertTrue(embraceSpan.start())
        val spanId = checkNotNull(embraceSpan.spanId)
        val spanFromService = checkNotNull(spansSink.getSpan(spanId))
        assertSame(spanFromService, embraceSpan)
        assertTrue(spanFromService.stop())
        assertFalse(embraceSpan.isRecording)
        verifyAndReturnSoleCompletedSpan("emb-test-span")
    }

    @Test
    fun `validate tracked spans update when service is flushed`() {
        initService()
        val embraceSpan = checkNotNull(spansService.createSpan(name = "test-span"))
        assertTrue(embraceSpan.start())
        val embraceSpanId = checkNotNull(embraceSpan.spanId)
        val parentSpan = checkNotNull(spansService.createSpan(name = "parent-span"))
        assertTrue(parentSpan.start())
        val parentSpanId = checkNotNull(parentSpan.spanId)
        val parentSpanFromService = checkNotNull(spansSink.getSpan(parentSpanId))
        assertTrue(parentSpanFromService.stop())
        currentSessionSpan.endSession()

        // completed span not available after flush
        assertNull(spansSink.getSpan(parentSpanId))

        // existing reference to completed span can still be used
        checkNotNull(spansService.createSpan(name = "child-span", parent = parentSpan))

        // active span from before flush is still available and working
        val activeSpanFromBeforeFlush = checkNotNull(spansSink.getSpan(embraceSpanId))
        assertTrue(activeSpanFromBeforeFlush.stop())
        verifyAndReturnSoleCompletedSpan("emb-test-span")
    }

    private fun initService() {
        val initModule = FakeInitModule(clock = clock)
        spansSink = initModule.spansSink
        currentSessionSpan = initModule.currentSessionSpan
        spansService = SpansServiceImpl(
            spansSink = spansSink,
            currentSessionSpan = currentSessionSpan,
            tracer = initModule.tracer
        )
        spansService.initializeService(TimeUnit.MILLISECONDS.toNanos(clock.now()))
    }

    private fun initAndStartNextSession() {
        initService()
        currentSessionSpan.endSession()
    }

    private fun verifyAndReturnSoleCompletedSpan(name: String): EmbraceSpanData {
        val currentSpans = spansSink.completedSpans()
        assertEquals(1, currentSpans.size)
        assertEquals(name, currentSpans[0].name)
        return currentSpans[0]
    }
}
