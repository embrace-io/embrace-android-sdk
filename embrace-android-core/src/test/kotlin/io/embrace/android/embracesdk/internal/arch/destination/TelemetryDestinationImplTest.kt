package io.embrace.android.embracesdk.internal.arch.destination

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.fakes.FakeAppStateTracker
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeClock.Companion.DEFAULT_FAKE_CURRENT_TIME
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakeEventService
import io.embrace.android.embracesdk.fakes.FakeSessionTracker
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.fakes.fakeSessionToken
import io.embrace.android.embracesdk.internal.arch.attrs.asPair
import io.embrace.android.embracesdk.internal.arch.attrs.embState
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.datasource.SpanEventImpl
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.otel.toEmbracePayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.semconv.SessionAttributes
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
    private lateinit var sessionTracker: FakeSessionTracker
    private lateinit var impl: TelemetryDestination
    private lateinit var appStateTracker: FakeAppStateTracker
    private lateinit var clock: FakeClock
    private lateinit var spanService: FakeSpanService
    private lateinit var eventService: FakeEventService
    private lateinit var currentSessionSpan: FakeCurrentSessionSpan
    private var sessionDataUpdated = false

    @Before
    fun setup() {
        sessionTracker = FakeSessionTracker()
        appStateTracker = FakeAppStateTracker()
        clock = FakeClock()
        spanService = FakeSpanService()
        eventService = FakeEventService()
        currentSessionSpan = FakeCurrentSessionSpan()
        impl = TelemetryDestinationImpl(
            sessionTracker = sessionTracker,
            appStateTracker = appStateTracker,
            clock = clock,
            spanService = spanService,
            eventService = eventService,
            currentSessionSpan = currentSessionSpan,
        ).also {
            it.sessionUpdateAction = { sessionDataUpdated = true }
        }
    }

    @Test
    fun `check expected values added to every OTel log`() {
        sessionTracker.newActiveSession(
            endSessionCallback = {},
            startSessionCallback = { fakeSessionToken().copy(sessionId = "fake-session-id") },
            postTransitionAppState = AppState.FOREGROUND
        )
        val expectedSchemaType = SchemaType.Log(
            TelemetryAttributes(
                customAttributes = mapOf(PrivateSpan.asPair())
            )
        )
        impl.addLog(
            schemaType = expectedSchemaType,
            severity = LogSeverity.ERROR,
            message = "test"
        )
        with(eventService.events.single()) {
            assertEquals(clock.now(), logTimeMs)
            assertEquals(expectedSchemaType, schemaType)
            assertEquals("test", message)
            assertEquals(Severity.ERROR.name, severity.name)
            assertFalse(isPrivate)
            assertEquals("fake-session-id", attributes[SessionAttributes.SESSION_ID])
            assertNotNull(attributes[embState.name])
        }
        verifyAndResetSessionUpdate()
    }

    @Test
    fun `check that private value is set`() {
        impl.addLog(
            schemaType = SchemaType.Log(TelemetryAttributes()),
            severity = LogSeverity.ERROR,
            message = "test",
            isPrivate = true
        )
        with(eventService.events.single()) {
            assertTrue(isPrivate)
        }
        impl.addLog(
            schemaType = SchemaType.Log(TelemetryAttributes()),
            severity = LogSeverity.ERROR,
            message = "test",
            isPrivate = false
        )
        with(eventService.events.last()) {
            assertFalse(isPrivate)
        }
    }

    @Test
    fun `foreground state matches the session a log is associated with`() {
        sessionTracker.newActiveSession(
            endSessionCallback = {},
            startSessionCallback = { fakeSessionToken().copy(sessionId = "foreground-session") },
            postTransitionAppState = AppState.FOREGROUND
        )
        appStateTracker.state = AppState.BACKGROUND
        impl.addLog(
            schemaType = SchemaType.Log(TelemetryAttributes()),
            severity = LogSeverity.ERROR,
            message = "test"
        )

        with(eventService.events.last()) {
            assertEquals(
                "foreground-session",
                attributes[SessionAttributes.SESSION_ID]
            )
            assertEquals("foreground", attributes[embState.name])
        }
    }

    @Test
    fun `use app state for background or foreground if no session exists`() {
        appStateTracker.state = AppState.BACKGROUND
        impl.addLog(
            schemaType = SchemaType.Log(TelemetryAttributes()),
            severity = LogSeverity.ERROR,
            message = "test"
        )

        with(eventService.events.last()) {
            assertNull(attributes[SessionAttributes.SESSION_ID])
            assertEquals("background", attributes[embState.name])
        }
    }

    @Test
    fun `timestamp can be overridden`() {
        val fakeTimeMs = DEFAULT_FAKE_CURRENT_TIME
        impl.addLog(
            schemaType = SchemaType.Log(TelemetryAttributes()),
            severity = LogSeverity.ERROR,
            message = "test",
            timestampMs = fakeTimeMs
        )

        with(eventService.events.last()) {
            assertEquals(fakeTimeMs, logTimeMs)
        }
    }

    @Test
    fun `only set current session info on log if instructed to`() {
        sessionTracker.newActiveSession(
            endSessionCallback = {},
            startSessionCallback = { fakeSessionToken().copy(sessionId = "foreground-session") },
            postTransitionAppState = AppState.FOREGROUND
        )
        impl.addLog(
            schemaType = SchemaType.Log(TelemetryAttributes()),
            severity = LogSeverity.ERROR,
            message = "test",
            addCurrentSessionInfo = false,
        )

        with(eventService.events.last()) {
            assertNull(attributes[SessionAttributes.SESSION_ID])
            assertNull(attributes[embState.name])
        }
    }

    @Test
    fun `no active session will result in log written without sessionId`() {
        sessionTracker.currentSession = fakeSessionToken().copy(sessionId = "", appState = AppState.BACKGROUND)
        impl.addLog(
            schemaType = SchemaType.Log(TelemetryAttributes()),
            severity = LogSeverity.ERROR,
            message = "test"
        )

        with(eventService.events.last()) {
            assertNull(attributes[SessionAttributes.SESSION_ID])
            assertEquals("background", attributes[embState.name])
        }
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
            name,
            startTimeMs,
            endTimeMs,
            errorCode,
            null,
            type,
            true,
            false,
            attributes
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
