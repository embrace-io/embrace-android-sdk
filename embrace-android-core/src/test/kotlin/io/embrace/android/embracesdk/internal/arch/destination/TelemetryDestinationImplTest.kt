package io.embrace.android.embracesdk.internal.arch.destination

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeClock.Companion.DEFAULT_FAKE_CURRENT_TIME
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakeEventService
import io.embrace.android.embracesdk.fakes.FakeMutableAttributeContainer
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.internal.arch.attrs.asPair
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.datasource.SpanEventImpl
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.Log
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.toEmbracePayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.tracing.StatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalApi::class, IncubatingApi::class)
internal class TelemetryDestinationImplTest {
    private lateinit var impl: TelemetryDestination
    private lateinit var clock: FakeClock
    private lateinit var spanService: FakeSpanService
    private lateinit var eventService: FakeEventService
    private lateinit var currentSessionSpan: FakeCurrentSessionSpan
    private var sessionDataUpdated = false

    @Before
    fun setup() {
        clock = FakeClock()
        spanService = FakeSpanService()
        eventService = FakeEventService()
        currentSessionSpan = FakeCurrentSessionSpan()
        impl = createDestination()
    }

    private fun createDestination(): TelemetryDestination =
        TelemetryDestinationImpl(
            clock = clock,
            spanService = spanService,
            eventService = eventService,
            currentSessionSpan = currentSessionSpan,
        ).also {
            it.sessionUpdateAction = { sessionDataUpdated = true }
        }

    @Test
    fun `check expected values added to every OTel log`() {
        impl = createDestination()
        val expectedSchemaType = Log(
            TelemetryAttributes(
                customAttributes = mapOf("foo" to "bar")
            )
        )
        impl.addLog(
            schemaType = expectedSchemaType,
            severity = LogSeverity.ERROR,
            message = "test"
        )
        eventService.eventData.single().assertFakeEvent(
            expectedTimestamp = clock.now().millisToNanos(),
            expectedMessage = "test",
            expectedSeverity = Severity.ERROR,
            expectedSchemaType = expectedSchemaType,
            expectedIsPrivate = false
        )
        verifyAndResetSessionUpdate()
    }

    private fun FakeEventService.FakeEventData.assertFakeEvent(
        expectedTimestamp: Long,
        expectedMessage: String?,
        expectedSeverity: Severity,
        expectedSchemaType: SchemaType,
        expectedIsPrivate: Boolean,
    ) {
        assertEquals(expectedTimestamp, timestamp)
        assertEquals(expectedMessage, body)
        assertEquals(expectedSeverity.name, severityNumber?.name)
        assertAttributes(
            expectedSchemaType = expectedSchemaType,
            expectedAdditionalAttributes = if (expectedIsPrivate) {
                mapOf(PrivateSpan.asPair())
            } else {
                emptyMap()
            }
        )
    }

    private fun FakeEventService.FakeEventData.assertAttributes(
        expectedSchemaType: SchemaType,
        expectedAdditionalAttributes: Map<String, String>
    ) {
        val attrContainer = FakeMutableAttributeContainer()
        checkNotNull(attributes).invoke(attrContainer)
        val logAttributes = attrContainer.attributes.mapValues { it.value.toString() }
        (expectedSchemaType.attributes() + expectedAdditionalAttributes).forEach { attr ->
            assertEquals(attr.value, logAttributes[attr.key])
        }
        assertTrue(logAttributes.hasEmbraceAttribute(expectedSchemaType.telemetryType))
    }

    @Test
    fun `check that private value is set`() {
        val expectedSchemaType = Log(TelemetryAttributes())
        impl.addLog(
            schemaType = expectedSchemaType,
            severity = LogSeverity.ERROR,
            message = "test",
            isPrivate = true
        )
        eventService.eventData.single().assertFakeEvent(
            expectedTimestamp = clock.now().millisToNanos(),
            expectedMessage = "test",
            expectedSeverity = Severity.ERROR,
            expectedSchemaType = expectedSchemaType,
            expectedIsPrivate = true
        )

        impl.addLog(
            schemaType = expectedSchemaType,
            severity = LogSeverity.ERROR,
            message = "test",
            isPrivate = false
        )
        eventService.eventData.last().assertFakeEvent(
            expectedTimestamp = clock.now().millisToNanos(),
            expectedMessage = "test",
            expectedSeverity = Severity.ERROR,
            expectedSchemaType = expectedSchemaType,
            expectedIsPrivate = false
        )
    }

    @Test
    fun `timestamp can be overridden`() {
        val expectedSchemaType = Log(TelemetryAttributes())
        val fakeTimeMs = DEFAULT_FAKE_CURRENT_TIME
        impl.addLog(
            schemaType = Log(TelemetryAttributes()),
            severity = LogSeverity.ERROR,
            message = "test",
            timestampMs = fakeTimeMs
        )

        eventService.eventData.single().assertFakeEvent(
            expectedTimestamp = fakeTimeMs.millisToNanos(),
            expectedMessage = "test",
            expectedSeverity = Severity.ERROR,
            expectedSchemaType = expectedSchemaType,
            expectedIsPrivate = false
        )
    }

    @Test
    fun `test start span capture`() {
        val span = impl.startSpanCapture(SchemaType.Breadcrumb("Whoops"), 5)
        assertNotNull(span)
        verifyAndResetSessionUpdate()
        span?.stop()
        verifyAndResetSessionUpdate()
    }

    @Test
    fun `test record successful span`() {
        val name = "success-span"
        val clock = FakeClock()
        val startTimeMs = clock.now()
        clock.tick(10)
        val endTimeMs = clock.now()
        impl.recordCompletedSpan(
            name,
            startTimeMs,
            endTimeMs,
            attributes = mapOf("foo" to "bar"),
            events = listOf(SpanEventImpl("event", clock.now().millisToNanos(), mapOf("key" to "value")))
        )
        val span = spanService.createdSpans.single()
        assertEquals(name, span.name)
        assertEquals(startTimeMs, span.spanStartTimeMs)
        assertEquals(endTimeMs, span.spanEndTimeMs)
        assertEquals(StatusCode.UNSET, span.status.statusCode)
        assertTrue(span.hasEmbraceAttribute(EmbType.Performance.Default))
        assertFalse(span.attributes.containsKey("emb.error_code"))
        assertEquals("bar", span.attributes["foo"])
        val event = span.events.single()
        assertEquals("event", event.name)
        assertEquals(clock.now().millisToNanos(), event.timestampNanos)
        assertEquals("value", event.attributes["key"])
        assertNull(span.parent)
        verifyAndResetSessionUpdate()
    }

    @Test
    fun `test record failed span with attributes`() {
        val name = "failed-span"
        val startTimeMs = 5L
        val endTimeMs = 15L
        val errorCode = ErrorCodeAttribute.Failure
        val type = EmbType.Performance.Network
        val attributes = mapOf("foo" to "bar")
        impl.recordCompletedSpan(
            name = name,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            errorCode = errorCode,
            parent = null,
            type = type,
            internal = true,
            private = false,
            attributes = attributes
        )
        val span = spanService.createdSpans.single()
        assertEquals(name, span.name)
        assertEquals(startTimeMs, span.spanStartTimeMs)
        assertEquals(endTimeMs, span.spanEndTimeMs)
        assertEquals(Span.Status.ERROR, span.status.statusCode.toEmbracePayload())
        assertTrue(span.hasEmbraceAttribute(type))
        assertTrue(span.hasEmbraceAttribute(errorCode))
        assertEquals(type, span.type)
        assertEquals(attributes + mapOf(type.asPair(), errorCode.asPair()), span.attributes)
        verifyAndResetSessionUpdate()
    }

    @Test
    fun `test start simple span`() {
        val name = "success-span"
        val startTimeMs = clock.now()
        val token = impl.startSpanCapture(
            name,
            startTimeMs,
        ) ?: error("Failed to create span")
        token.stop()

        val span = spanService.createdSpans.single()
        assertEquals(name, span.name)
        assertEquals(startTimeMs, span.spanStartTimeMs)
        assertEquals(StatusCode.UNSET, span.status.statusCode)
        assertTrue(span.hasEmbraceAttribute(EmbType.Performance.Default))
        assertFalse(span.attributes.containsKey("emb.error_code"))
        assertNull(span.parent)
        verifyAndResetSessionUpdate()
    }

    @Test
    fun `test session span events`() {
        currentSessionSpan.readySession()
        val current = checkNotNull(currentSessionSpan.current())
        val schemaType = SchemaType.Breadcrumb("Hi")
        impl.addSessionEvent(schemaType, 5)
        verifyAndResetSessionUpdate()

        val event = (current as FakeEmbraceSdkSpan).events.single()
        assertEquals("emb-${schemaType.fixedObjectName}", event.name)

        impl.removeSessionEvents(schemaType.telemetryType)
        assertTrue(currentSessionSpan.addedEvents.isEmpty())
        verifyAndResetSessionUpdate()
    }

    @Test
    fun `test session span attributes`() {
        currentSessionSpan.readySession()
        val current = checkNotNull(currentSessionSpan.current())
        impl.addSessionAttribute("foo", "bar")
        assertEquals("bar", current.attributes()["foo"])
        verifyAndResetSessionUpdate()

        impl.removeSessionAttribute("foo")
        assertNull(current.attributes()["foo"])
        verifyAndResetSessionUpdate()
    }

    private fun verifyAndResetSessionUpdate() {
        assertTrue(sessionDataUpdated)
        sessionDataUpdated = false
    }
}
