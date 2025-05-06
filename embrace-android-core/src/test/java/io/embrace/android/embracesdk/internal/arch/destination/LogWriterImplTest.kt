package io.embrace.android.embracesdk.internal.arch.destination

import io.embrace.android.embracesdk.assertions.findAttributeValue
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeClock.Companion.DEFAULT_FAKE_CURRENT_TIME
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryLogger
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.otel.attrs.asOtelAttributeKey
import io.embrace.android.embracesdk.internal.otel.attrs.asPair
import io.embrace.android.embracesdk.internal.otel.attrs.embState
import io.embrace.android.embracesdk.internal.otel.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.otel.spans.getAttribute
import io.embrace.android.embracesdk.internal.otel.spans.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.session.id.SessionData
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.semconv.incubating.LogIncubatingAttributes
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
                    configService = FakeConfigService(),
                    customAttributes = mapOf(PrivateSpan.asPair())
                )
            ),
            severity = Severity.ERROR,
            message = "test"
        )
        with(logger.builders.single()) {
            assertEquals("test", body)
            assertEquals(Severity.ERROR, severity)
            assertEquals(Severity.ERROR.name, severity.name)
            assertEquals("fake-session-id", attributes.getAttribute(SessionIncubatingAttributes.SESSION_ID))
            assertNotNull(attributes.getAttribute(embState))
            assertNotNull(attributes.getAttribute(LogIncubatingAttributes.LOG_RECORD_UID))
            assertTrue(attributes.hasEmbraceAttribute(PrivateSpan))
            assertEquals(clock.nowInNanos(), timestampEpochNanos)
            assertEquals(0, observedTimestampEpochNanos)
        }
    }

    @Test
    fun `check that private value is set`() {
        logWriterImpl.addLog(
            schemaType = SchemaType.Log(
                TelemetryAttributes(
                    configService = FakeConfigService()
                )
            ),
            severity = Severity.ERROR,
            message = "test",
            isPrivate = true
        )
        with(logger.builders.single()) {
            assertTrue(attributes.hasEmbraceAttribute(PrivateSpan))
        }
        logWriterImpl.addLog(
            schemaType = SchemaType.Log(
                TelemetryAttributes(
                    configService = FakeConfigService()
                )
            ),
            severity = Severity.ERROR,
            message = "test",
            isPrivate = false
        )
        with(logger.builders.last()) {
            assertFalse(attributes.hasEmbraceAttribute(PrivateSpan))
        }
    }

    @Test
    fun `foreground state matches the session a log is associated with`() {
        sessionIdTracker.setActiveSession("foreground-session", true)
        processStateService.isInBackground = true
        logWriterImpl.addLog(
            schemaType = SchemaType.Log(
                TelemetryAttributes(
                    configService = FakeConfigService()
                )
            ),
            severity = Severity.ERROR,
            message = "test"
        )

        with(logger.builders.last()) {
            assertEquals(
                "foreground-session",
                attributes.findAttributeValue(SessionIncubatingAttributes.SESSION_ID.key)
            )
            assertEquals("foreground", attributes.findAttributeValue(embState.asOtelAttributeKey().key))
        }
    }

    @Test
    fun `use app state for background or foreground if no session exists`() {
        sessionIdTracker.sessionData = null
        processStateService.isInBackground = true
        logWriterImpl.addLog(
            schemaType = SchemaType.Log(
                TelemetryAttributes(
                    configService = FakeConfigService()
                )
            ),
            severity = Severity.ERROR,
            message = "test"
        )

        with(logger.builders.last()) {
            assertNull(attributes.findAttributeValue(SessionIncubatingAttributes.SESSION_ID.key))
            assertEquals("background", attributes.findAttributeValue(embState.asOtelAttributeKey().key))
        }
    }

    @Test
    fun `timestamp can be overridden`() {
        val fakeTimeMs = DEFAULT_FAKE_CURRENT_TIME
        logWriterImpl.addLog(
            schemaType = SchemaType.Log(
                TelemetryAttributes(
                    configService = FakeConfigService()
                )
            ),
            severity = Severity.ERROR,
            message = "test",
            timestampMs = fakeTimeMs
        )

        with(logger.builders.last()) {
            assertEquals(fakeTimeMs.millisToNanos(), timestampEpochNanos)
            assertEquals(0, observedTimestampEpochNanos)
        }
    }

    @Test
    fun `only set current session info on log if instructed to`() {
        sessionIdTracker.setActiveSession("foreground-session", true)
        logWriterImpl.addLog(
            schemaType = SchemaType.Log(
                TelemetryAttributes(
                    configService = FakeConfigService()
                )
            ),
            severity = Severity.ERROR,
            message = "test",
            addCurrentSessionInfo = false,
        )

        with(logger.builders.last()) {
            assertNull(attributes.findAttributeValue(SessionIncubatingAttributes.SESSION_ID.key))
            assertNull(attributes.getAttribute(embState))
        }
    }

    @Test
    fun `no activity session will result in log written without sessionId`() {
        sessionIdTracker.sessionData = SessionData("", false)
        logWriterImpl.addLog(
            schemaType = SchemaType.Log(
                TelemetryAttributes(
                    configService = FakeConfigService()
                )
            ),
            severity = Severity.ERROR,
            message = "test"
        )

        with(logger.builders.last()) {
            assertNull(attributes.findAttributeValue(SessionIncubatingAttributes.SESSION_ID.key))
            assertEquals("background", attributes.findAttributeValue(embState.asOtelAttributeKey().key))
        }
    }
}
