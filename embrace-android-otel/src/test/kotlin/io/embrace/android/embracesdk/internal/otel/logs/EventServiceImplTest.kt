package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryLogger
import io.opentelemetry.kotlin.logging.SeverityNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EventServiceImplTest {
    lateinit var sdkLogger: FakeOpenTelemetryLogger
    lateinit var impl: EventServiceImpl

    @Before
    fun setup() {
        sdkLogger = FakeOpenTelemetryLogger()
        impl = EventServiceImpl(sdkLoggerProvider = { sdkLogger })
        impl.initializeService(100L)
    }

    @Test
    fun `event service needs initialization`() {
        val notInitializedLogger = EventServiceImpl(sdkLoggerProvider = { sdkLogger })
        assertFalse(notInitializedLogger.initialized())
        notInitializedLogger.log(
            eventName = null,
            body = "test",
            timestamp = 1000L,
            observedTimestamp = 1005L,
            context = null,
            severityNumber = SeverityNumber.ERROR,
            severityText = "boo",
        ) { }

        assertTrue(sdkLogger.logs.isEmpty())
    }

    @Test
    fun `event emitted with the given values`() {
        assertTrue(impl.initialized())
        impl.log(
            eventName = "my.event",
            body = "test",
            timestamp = 1000L,
            observedTimestamp = 1005L,
            context = null,
            severityNumber = SeverityNumber.ERROR,
            severityText = "boo",
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
        }
    }
}
