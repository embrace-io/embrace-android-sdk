package io.embrace.android.embracesdk.internal.arch.destination

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeClock.Companion.DEFAULT_FAKE_CURRENT_TIME
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryLogger
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.opentelemetry.embState
import io.embrace.android.embracesdk.internal.session.id.SessionData
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.logging.SeverityNumber
import io.opentelemetry.semconv.incubating.LogIncubatingAttributes
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalApi::class)
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
                    customAttributes = mapOf(PrivateSpan.toEmbraceKeyValuePair())
                )
            ),
            severity = SeverityNumber.ERROR,
            message = "test"
        )
        with(logger.logs.single()) {
            assertEquals("test", body)
            assertEquals(SeverityNumber.ERROR, severityNumber)
            assertEquals(SeverityNumber.ERROR.name, severityNumber?.name)
            assertEquals("fake-session-id", attributes()[SessionIncubatingAttributes.SESSION_ID.key])
            assertNotNull(attributes()[embState.attributeKey])
            assertNotNull(attributes()[LogIncubatingAttributes.LOG_RECORD_UID.key])
            assertTrue(attributes()[PrivateSpan.key.attributeKey] != null)
            assertEquals(clock.nowInNanos(), timestampNs)
            assertNull(observedTimestampNs)
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
            severity = SeverityNumber.ERROR,
            message = "test",
            isPrivate = true
        )
        with(logger.logs.single()) {
            assertTrue(attributes()[PrivateSpan.key.attributeKey] != null)
        }
        logWriterImpl.addLog(
            schemaType = SchemaType.Log(
                TelemetryAttributes(
                    configService = FakeConfigService()
                )
            ),
            severity = SeverityNumber.ERROR,
            message = "test",
            isPrivate = false
        )
        with(logger.logs.last()) {
            assertFalse(attributes()[PrivateSpan.key.attributeKey] != null)
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
            severity = SeverityNumber.ERROR,
            message = "test"
        )

        with(logger.logs.last()) {
            assertEquals(
                "foreground-session",
                attributes()[SessionIncubatingAttributes.SESSION_ID.key]
            )
            assertEquals("foreground", attributes()[embState.attributeKey])
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
            severity = SeverityNumber.ERROR,
            message = "test"
        )

        with(logger.logs.last()) {
            assertNull(attributes()[SessionIncubatingAttributes.SESSION_ID.key])
            assertEquals("background", attributes()[embState.attributeKey])
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
            severity = SeverityNumber.ERROR,
            message = "test",
            timestampMs = fakeTimeMs
        )

        with(logger.logs.last()) {
            assertEquals(fakeTimeMs.millisToNanos(), timestampNs)
            assertNull(observedTimestampNs)
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
            severity = SeverityNumber.ERROR,
            message = "test",
            addCurrentSessionInfo = false,
        )

        with(logger.logs.last()) {
            assertNull(attributes()[SessionIncubatingAttributes.SESSION_ID.key])
            assertNull(attributes()[embState.attributeKey])
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
            severity = SeverityNumber.ERROR,
            message = "test"
        )

        with(logger.logs.last()) {
            assertNull(attributes()[SessionIncubatingAttributes.SESSION_ID.key])
            assertEquals("background", attributes()[embState.attributeKey])
        }
    }
}
