package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryLogger
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.logging.model.SeverityNumber
import io.opentelemetry.kotlin.semconv.IncubatingApi
import io.opentelemetry.kotlin.semconv.LogAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalApi::class, IncubatingApi::class)
class EventServiceImplTest {
    val sessionAttributeName = "session-attr"
    lateinit var sdkLogger: FakeOpenTelemetryLogger
    lateinit var impl: EventServiceImpl

    @Before
    fun setup() {
        sdkLogger = FakeOpenTelemetryLogger()
        impl = EventServiceImpl(
            sdkLoggerProvider = { sdkLogger }
        )
        impl.initializeService(100L)
        impl.setMetadataProvider { mapOf(sessionAttributeName to "foo") }
    }

    @Test
    fun `event service needs initialization`() {
        val notInitializedLogger = EventServiceImpl(
            sdkLoggerProvider = { sdkLogger }
        )
        assertFalse(notInitializedLogger.initialized())
        notInitializedLogger.log(
            eventName = null,
            body = "test",
            timestamp = 1000L,
            observedTimestamp = 1005L,
            context = null,
            severityNumber = SeverityNumber.ERROR,
            severityText = "boo",
            addCurrentMetadata = true,
        ) { }

        assertTrue(sdkLogger.logs.isEmpty())
    }

    @Test
    fun `check expected values added to every event`() {
        assertTrue(impl.initialized())
        impl.log(
            eventName = "my.event",
            body = "test",
            timestamp = 1000L,
            observedTimestamp = 1005L,
            context = null,
            severityNumber = SeverityNumber.ERROR,
            severityText = "boo",
            addCurrentMetadata = true,
        ) {
            setStringAttribute("custom", "attr")
        }

        with(sdkLogger.logs.single()) {
            assertEquals("my.event", eventName)
            assertEquals(1000L, timestamp)
            assertEquals(1005L, observedTimestamp)
            assertEquals("test", body)
            assertEquals(SeverityNumber.ERROR, severityNumber)
            assertEquals("boo", severityText)
            assertEquals("attr", attributes["custom"])
            assertEquals("foo", attributes[sessionAttributeName])
            assertNotNull(attributes[LogAttributes.LOG_RECORD_UID])
        }
    }

    @Test
    fun `existing log id not overridden`() {
        impl.log(
            eventName = null,
            body = "test",
            timestamp = 1000L,
            observedTimestamp = 1005L,
            context = null,
            severityNumber = SeverityNumber.ERROR,
            severityText = "boo",
            addCurrentMetadata = true,
        ) {
            setStringAttribute(LogAttributes.LOG_RECORD_UID, "foo")
        }

        with(sdkLogger.logs.single()) {
            assertEquals("foo", attributes[LogAttributes.LOG_RECORD_UID])
        }
    }

    @Test
    fun `current metadata added only requested`() {
        impl.log(
            eventName = null,
            body = "test",
            timestamp = 1000L,
            observedTimestamp = 1005L,
            context = null,
            severityNumber = SeverityNumber.ERROR,
            severityText = "boo",
            addCurrentMetadata = false,
        ) { }

        with(sdkLogger.logs.single()) {
            assertFalse(attributes.containsKey(sessionAttributeName))
        }
    }
}
