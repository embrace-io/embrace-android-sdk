package io.embrace.android.embracesdk.internal.arch.destination

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeClock.Companion.DEFAULT_FAKE_CURRENT_TIME
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryLogger
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.otel.attrs.asPair
import io.embrace.android.embracesdk.internal.otel.attrs.embState
import io.embrace.android.embracesdk.internal.otel.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.session.id.SessionData
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
internal class LogWriterImplTest {
    private lateinit var logger: FakeOpenTelemetryLogger
    private lateinit var sessionIdTracker: FakeSessionIdTracker
    private lateinit var logWriterImpl: LogWriterImpl
    private lateinit var processStateService: FakeProcessStateService
    private lateinit var clock: FakeClock

    @Before
    fun setup() {
        sessionIdTracker = FakeSessionIdTracker()
        logger = FakeOpenTelemetryLogger()
        processStateService = FakeProcessStateService()
        clock = FakeClock()
        logWriterImpl = LogWriterImpl(
            logger = logger,
            sessionIdTracker = sessionIdTracker,
            processStateService = processStateService,
            clock = clock,
        )
    }

    @Test
    fun `check expected values added to every OTel log`() {
        sessionIdTracker.setActiveSession("fake-session-id", true)
        logWriterImpl.addLog(
            schemaType = SchemaType.Log(
                TelemetryAttributes(
                    customAttributes = mapOf(PrivateSpan.asPair())
                )
            ),
            severity = Severity.ERROR,
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
        logWriterImpl.addLog(
            schemaType = SchemaType.Log(TelemetryAttributes()),
            severity = Severity.ERROR,
            message = "test",
            isPrivate = true
        )
        with(logger.logs.single()) {
            assertTrue(attributes[PrivateSpan.key.name] != null)
        }
        logWriterImpl.addLog(
            schemaType = SchemaType.Log(TelemetryAttributes()),
            severity = Severity.ERROR,
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
        logWriterImpl.addLog(
            schemaType = SchemaType.Log(TelemetryAttributes()),
            severity = Severity.ERROR,
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
        logWriterImpl.addLog(
            schemaType = SchemaType.Log(TelemetryAttributes()),
            severity = Severity.ERROR,
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
        logWriterImpl.addLog(
            schemaType = SchemaType.Log(TelemetryAttributes()),
            severity = Severity.ERROR,
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
        logWriterImpl.addLog(
            schemaType = SchemaType.Log(TelemetryAttributes()),
            severity = Severity.ERROR,
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
        logWriterImpl.addLog(
            schemaType = SchemaType.Log(TelemetryAttributes()),
            severity = Severity.ERROR,
            message = "test"
        )

        with(logger.logs.last()) {
            assertNull(attributes[SessionAttributes.SESSION_ID])
            assertEquals("background", attributes[embState.name])
        }
    }
}
