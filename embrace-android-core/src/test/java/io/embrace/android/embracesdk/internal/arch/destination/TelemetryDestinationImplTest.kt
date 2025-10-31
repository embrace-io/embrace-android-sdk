package io.embrace.android.embracesdk.internal.arch.destination

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeClock.Companion.DEFAULT_FAKE_CURRENT_TIME
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryLogger
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.internal.arch.attrs.asPair
import io.embrace.android.embracesdk.internal.arch.attrs.embState
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.otel.toEmbracePayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.id.SessionData
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.semconv.LogAttributes
import io.embrace.opentelemetry.kotlin.semconv.SessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalApi::class, IncubatingApi::class)
internal class TelemetryDestinationImplTest {
    private lateinit var logger: FakeOpenTelemetryLogger
    private lateinit var sessionIdTracker: FakeSessionIdTracker
    private lateinit var impl: TelemetryDestination
    private lateinit var processStateService: FakeProcessStateService
    private lateinit var clock: FakeClock
    private lateinit var spanService: FakeSpanService
    private lateinit var currentSessionSpan: FakeCurrentSessionSpan

    @Before
    fun setup() {
        sessionIdTracker = FakeSessionIdTracker()
        logger = FakeOpenTelemetryLogger()
        processStateService = FakeProcessStateService()
        clock = FakeClock()
        spanService = FakeSpanService()
        currentSessionSpan = FakeCurrentSessionSpan()
        impl = TelemetryDestinationImpl(
            logger = logger,
            sessionIdTracker = sessionIdTracker,
            processStateService = processStateService,
            clock = clock,
            spanService = spanService,
            currentSessionSpan = currentSessionSpan,
        )
    }

    @Test
    fun `check expected values added to every OTel log`() {
        sessionIdTracker.setActiveSession("fake-session-id", true)
        impl.addLog(
            schemaType = SchemaType.Log(
                TelemetryAttributes(
                    customAttributes = mapOf(PrivateSpan.asPair())
                )
            ),
            severity = LogSeverity.ERROR,
            message = "test"
        )
        with(logger.logs.single()) {
            assertEquals("test", body)
            assertEquals(Severity.ERROR.name, severityNumber?.name)
            assertEquals("fake-session-id", attributes[SessionAttributes.SESSION_ID])
            assertNotNull(attributes[embState.name])
            assertNotNull(attributes[LogAttributes.LOG_RECORD_UID])
            assertTrue(attributes[PrivateSpan.key.name] != null)
            assertEquals(clock.now().millisToNanos(), timestamp)
            assertNull(observedTimestamp)
        }
    }

    @Test
    fun `check that private value is set`() {
        impl.addLog(
            schemaType = SchemaType.Log(TelemetryAttributes()),
            severity = LogSeverity.ERROR,
            message = "test",
            isPrivate = true
        )
        with(logger.logs.single()) {
            assertTrue(attributes[PrivateSpan.key.name] != null)
        }
        impl.addLog(
            schemaType = SchemaType.Log(TelemetryAttributes()),
            severity = LogSeverity.ERROR,
            message = "test",
            isPrivate = false
        )
        with(logger.logs.last()) {
            assertFalse(attributes[PrivateSpan.key.name] != null)
        }
    }

    @Test
    fun `foreground state matches the session a log is associated with`() {
        sessionIdTracker.setActiveSession("foreground-session", true)
        processStateService.isInBackground = true
        impl.addLog(
            schemaType = SchemaType.Log(TelemetryAttributes()),
            severity = LogSeverity.ERROR,
            message = "test"
        )

        with(logger.logs.last()) {
            assertEquals(
                "foreground-session",
                attributes[SessionAttributes.SESSION_ID]
            )
            assertEquals("foreground", attributes[embState.name])
        }
    }

    @Test
    fun `use app state for background or foreground if no session exists`() {
        sessionIdTracker.sessionData = null
        processStateService.isInBackground = true
        impl.addLog(
            schemaType = SchemaType.Log(TelemetryAttributes()),
            severity = LogSeverity.ERROR,
            message = "test"
        )

        with(logger.logs.last()) {
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

        with(logger.logs.last()) {
            assertEquals(fakeTimeMs.millisToNanos(), timestamp)
            assertNull(observedTimestamp)
        }
    }

    @Test
    fun `only set current session info on log if instructed to`() {
        sessionIdTracker.setActiveSession("foreground-session", true)
        impl.addLog(
            schemaType = SchemaType.Log(TelemetryAttributes()),
            severity = LogSeverity.ERROR,
            message = "test",
            addCurrentSessionInfo = false,
        )

        with(logger.logs.last()) {
            assertNull(attributes[SessionAttributes.SESSION_ID])
            assertNull(attributes[embState.name])
        }
    }

    @Test
    fun `no activity session will result in log written without sessionId`() {
        sessionIdTracker.sessionData = SessionData("", false)
        impl.addLog(
            schemaType = SchemaType.Log(TelemetryAttributes()),
            severity = LogSeverity.ERROR,
            message = "test"
        )

        with(logger.logs.last()) {
            assertNull(attributes[SessionAttributes.SESSION_ID])
            assertEquals("background", attributes[embState.name])
        }
    }

    @Test
    fun `test start span capture`() {
        val span = impl.startSpanCapture(SchemaType.Breadcrumb("Whoops"), 5)
        assertNotNull(span)
        span?.stop()
    }

    @Test
    fun `test record completed span`() {
        val name = "name"
        val startTimeMs = 5L
        val endTimeMs = 5L
        val errorCode = ErrorCode.FAILURE.name
        val type = EmbType.Performance.Network
        val attributes = mapOf("foo" to "bar")
        impl.recordCompletedSpan(
            name,
            startTimeMs,
            endTimeMs,
            errorCode,
            type,
            attributes
        )
        val span = spanService.createdSpans.single()
        assertEquals(name, span.name)
        assertEquals(startTimeMs, span.spanStartTimeMs)
        assertEquals(endTimeMs, span.spanEndTimeMs)
        assertEquals(Span.Status.ERROR, span.status.statusCode.toEmbracePayload())
        assertTrue(span.hasEmbraceAttribute(type))
        assertTrue(span.hasEmbraceAttribute(ErrorCodeAttribute.Failure))
        assertEquals(type, span.type)
        assertEquals(attributes + mapOf(type.asPair(), ErrorCodeAttribute.Failure.asPair()), span.attributes)
    }

    @Test
    fun `test session span events`() {
        currentSessionSpan.readySession()
        val current = checkNotNull(currentSessionSpan.current())
        val schemaType = SchemaType.Breadcrumb("Hi")
        impl.addSessionEvent(schemaType, 5)

        val event = (current as FakeEmbraceSdkSpan).events.single()
        assertEquals("emb-${schemaType.fixedObjectName}", event.name)

        impl.removeSessionEvents(schemaType.telemetryType)
        assertTrue(currentSessionSpan.addedEvents.isEmpty())
    }

    @Test
    fun `test session span attributes`() {
        currentSessionSpan.readySession()
        val current = checkNotNull(currentSessionSpan.current())
        impl.addSessionAttribute("foo", "bar")
        assertEquals("bar", current.attributes()["foo"])

        impl.removeSessionAttribute("foo")
        assertNull(current.attributes()["foo"])
    }
}
