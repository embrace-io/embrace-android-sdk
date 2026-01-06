package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryLogger
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.logging.model.SeverityNumber
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.semconv.LogAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalApi::class, IncubatingApi::class)
class EventServiceImplTest {
    lateinit var sdkLogger: FakeOpenTelemetryLogger
    lateinit var impl: EventServiceImpl

    @Before
    fun setup() {
        sdkLogger = FakeOpenTelemetryLogger()
        impl = EventServiceImpl(
            sdkLoggerSupplier = { sdkLogger }
        )
        impl.initializeService(100L)
    }

    @Test
    fun `event service needs initialization`() {
        val notInitializedLogger = EventServiceImpl(
            sdkLoggerSupplier = { sdkLogger }
        )
        assertFalse(notInitializedLogger.initialized())
        notInitializedLogger.log(
            logTimeMs = 1000L,
            schemaType = SchemaType.Log(TelemetryAttributes()),
            severity = Severity.ERROR,
            message = "test",
            isPrivate = true,
            addCurrentMetadata = false,
        )

        assertTrue(sdkLogger.logs.isEmpty())
    }

    @Test
    fun `check expected values added to every event`() {
        assertTrue(impl.initialized())
        impl.log(
            logTimeMs = 1000L,
            schemaType = SchemaType.Log(TelemetryAttributes(customAttributes = mapOf("custom" to "attr"))),
            severity = Severity.ERROR,
            message = "test",
            isPrivate = true,
            addCurrentMetadata = false,
        )

        with(sdkLogger.logs.single()) {
            assertEquals(1000L, timestamp?.nanosToMillis())
            assertEquals("test", body)
            assertEquals(SeverityNumber.ERROR, severityNumber)
            assertEquals("true", attributes[PrivateSpan.key.name])
            assertEquals("attr", attributes["custom"])
            assertNotNull(attributes[LogAttributes.LOG_RECORD_UID])
        }
    }

    @Test
    fun `no extra attribute added when event is non-private`() {
        impl.log(
            logTimeMs = 1000L,
            schemaType = SchemaType.Log(TelemetryAttributes()),
            severity = Severity.INFO,
            message = "test",
            isPrivate = false,
            addCurrentMetadata = false,
        )

        with(sdkLogger.logs.single()) {
            assertFalse(attributes.containsKey(PrivateSpan.key.name))
        }
    }

    @Test
    fun `existing log id not overridden`() {
        impl.log(
            logTimeMs = 1000L,
            schemaType = SchemaType.Log(TelemetryAttributes(customAttributes = mapOf(LogAttributes.LOG_RECORD_UID to "foo"))),
            severity = Severity.INFO,
            message = "test",
            isPrivate = false,
            addCurrentMetadata = false,
        )

        with(sdkLogger.logs.single()) {
            assertEquals("foo", attributes[LogAttributes.LOG_RECORD_UID])
        }
    }

    @Test
    fun `current metadata added only requested`() {
        val sessionAttributeName = "session-attr"
        impl.setMetadataProvider { mapOf(sessionAttributeName to "foo") }
        impl.log(
            logTimeMs = 1000L,
            schemaType = SchemaType.Log(TelemetryAttributes(customAttributes = mapOf("custom" to "attr"))),
            severity = Severity.INFO,
            message = "add-metadata",
            isPrivate = false,
            addCurrentMetadata = true,
        )

        impl.log(
            logTimeMs = 1000L,
            schemaType = SchemaType.Log(TelemetryAttributes(customAttributes = mapOf("custom" to "attr"))),
            severity = Severity.INFO,
            message = "test",
            isPrivate = false,
            addCurrentMetadata = false,
        )

        assertEquals(2, sdkLogger.logs.size)
        sdkLogger.logs.forEach { log ->
            with(log) {
                assertEquals(1000L, timestamp?.nanosToMillis())
                assertEquals(SeverityNumber.INFO, severityNumber)
                assertEquals("attr", attributes["custom"])
                assertNotNull(attributes[LogAttributes.LOG_RECORD_UID])
                if (body == "add-metadata") {
                    assertEquals("foo", attributes[sessionAttributeName])
                } else {
                    assertFalse(attributes.containsKey(sessionAttributeName))
                }
            }
        }
    }
}
