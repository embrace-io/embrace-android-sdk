package io.embrace.android.embracesdk.internal.arch.schema

import io.embrace.android.embracesdk.fakes.TestUuidSource
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.semconv.ExceptionAttributes
import io.opentelemetry.kotlin.semconv.SessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class TelemetryAttributesTest {

    private lateinit var customAttributes: Map<String, String>
    private lateinit var telemetryAttributes: TelemetryAttributes
    private lateinit var otelSessionId: String

    @Before
    fun setup() {
        customAttributes = mapOf("custom" to "attributeValue")
        otelSessionId = TestUuidSource().createUuid()
    }

    @Test
    fun `only schema properties`() {
        telemetryAttributes = TelemetryAttributes()
        telemetryAttributes.setAttribute(SessionAttributes.SESSION_ID, otelSessionId)
        telemetryAttributes.setAttribute(ExceptionAttributes.EXCEPTION_TYPE, "exceptionValue")
        val attributes = telemetryAttributes.snapshot()
        assertEquals(2, attributes.size)
        assertEquals(otelSessionId, attributes[SessionAttributes.SESSION_ID])
        assertEquals("exceptionValue", attributes[ExceptionAttributes.EXCEPTION_TYPE])
        assertEquals(otelSessionId, telemetryAttributes.getAttribute(SessionAttributes.SESSION_ID))
        assertEquals("exceptionValue", telemetryAttributes.getAttribute(ExceptionAttributes.EXCEPTION_TYPE))
    }

    @Test
    fun `all attributes types`() {
        telemetryAttributes = TelemetryAttributes(
            customAttributes = customAttributes
        )
        val otelSessionIdKey = SessionAttributes.SESSION_ID
        telemetryAttributes.setAttribute(otelSessionIdKey, otelSessionId)

        val attributes = telemetryAttributes.snapshot()
        assertEquals("attributeValue", attributes["custom"])
        assertEquals(otelSessionId, attributes[otelSessionIdKey])
    }

    @Test
    fun `overwritten values returned`() {
        val newOtelSessionId = TestUuidSource().createUuid()
        telemetryAttributes = TelemetryAttributes()
        val otelSessionIdKey = SessionAttributes.SESSION_ID
        telemetryAttributes.setAttribute(otelSessionIdKey, otelSessionId)
        telemetryAttributes.setAttribute(otelSessionIdKey, newOtelSessionId)

        val attributes = telemetryAttributes.snapshot()
        assertEquals(1, attributes.size)
        assertEquals(newOtelSessionId, attributes[otelSessionIdKey])
    }

    @Test
    fun `schema attribute values take priority if the same key is used`() {
        val newOtelSessionId = TestUuidSource().createUuid()
        telemetryAttributes = TelemetryAttributes(
            customAttributes = mapOf(SessionAttributes.SESSION_ID to otelSessionId)
        )
        telemetryAttributes.setAttribute(SessionAttributes.SESSION_ID, newOtelSessionId)
        val attributes = telemetryAttributes.snapshot()
        assertEquals(1, attributes.size)
        assertEquals(newOtelSessionId, attributes[SessionAttributes.SESSION_ID])
    }

    @Test
    fun `log properties and session properties are included in the attributes`() {
        telemetryAttributes = TelemetryAttributes(
            customAttributes = customAttributes
        )
        telemetryAttributes.setAttribute(SessionAttributes.SESSION_ID, otelSessionId)

        val attributes = telemetryAttributes.snapshot()
        assertEquals(2, attributes.size)
    }

    @Test
    fun `blankish values skipped when directed to do so`() {
        telemetryAttributes = TelemetryAttributes()
        val blankishValues = listOf("", " ", "null", "NULL")

        // Give me Union types, plz
        val otelSessionIdKey = SessionAttributes.SESSION_ID
        blankishValues.forEach { value ->
            telemetryAttributes.setAttribute(otelSessionIdKey, value, true)
            assertEquals(value, telemetryAttributes.getAttribute(otelSessionIdKey))
        }

        telemetryAttributes.setAttribute(otelSessionIdKey, "test")

        blankishValues.forEach { value ->
            telemetryAttributes.setAttribute(otelSessionIdKey, value, false)
            assertEquals("test", telemetryAttributes.getAttribute(otelSessionIdKey))
        }

        blankishValues.forEach { value ->
            telemetryAttributes.setAttribute(EmbSessionAttributes.EMB_PROCESS_IDENTIFIER, value, true)
            assertEquals(value, telemetryAttributes.getAttribute(EmbSessionAttributes.EMB_PROCESS_IDENTIFIER))
        }

        telemetryAttributes.setAttribute(EmbSessionAttributes.EMB_PROCESS_IDENTIFIER, "test")

        blankishValues.forEach { value ->
            telemetryAttributes.setAttribute(EmbSessionAttributes.EMB_PROCESS_IDENTIFIER, value, false)
            assertEquals("test", telemetryAttributes.getAttribute(EmbSessionAttributes.EMB_PROCESS_IDENTIFIER))
        }
    }
}
