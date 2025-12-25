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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalApi::class, IncubatingApi::class)
class EventServiceImplTest {
    lateinit var logger: FakeOpenTelemetryLogger
    lateinit var impl: EventServiceImpl

    @Before
    fun setup() {
        logger = FakeOpenTelemetryLogger()
        impl = EventServiceImpl(
            loggerProvider = { logger }
        )
    }

    @Test
    fun `check expected values added to every event`() {
        val embraceAttributes = mapOf("foo" to "bar")
        impl.log(
            logTimeMs = 1000L,
            schemaType = SchemaType.Log(TelemetryAttributes(customAttributes = mapOf("custom" to "attr"))),
            severity = Severity.ERROR,
            message = "test",
            isPrivate = true,
            embraceAttributes = embraceAttributes
        )

        with(logger.logs.single()) {
            assertEquals(1000L, timestamp?.nanosToMillis())
            assertEquals("test", body)
            assertEquals(SeverityNumber.ERROR, severityNumber)
            assertEquals("true", attributes[PrivateSpan.key.name])
            assertEquals("attr", attributes["custom"])
            assertEquals("bar", attributes["foo"])
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
            embraceAttributes = emptyMap(),
        )

        with(logger.logs.single()) {
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
            embraceAttributes = emptyMap(),
        )

        with(logger.logs.single()) {
            assertEquals("foo", attributes[LogAttributes.LOG_RECORD_UID])
        }
    }
}
