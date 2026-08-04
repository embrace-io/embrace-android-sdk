package io.embrace.android.embracesdk.internal.arch.schema

import io.embrace.android.embracesdk.fakes.TestUuidSource
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.semconv.ExceptionAttributes
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class TelemetryAttributesTest {

    private lateinit var customAttributes: Map<String, String>
    private lateinit var telemetryAttributes: TelemetryAttributes
    private lateinit var userSessionId: String

    @Before
    fun setup() {
        customAttributes = mapOf("custom" to "attributeValue")
        userSessionId = TestUuidSource().createUuid()
    }

    @Test
    fun `only schema properties`() {
        telemetryAttributes = TelemetryAttributes()
        telemetryAttributes.setAttribute(EmbSessionAttributes.EMB_USER_SESSION_ID, userSessionId)
        telemetryAttributes.setAttribute(ExceptionAttributes.EXCEPTION_TYPE, "exceptionValue")
        val attributes = telemetryAttributes.snapshot()
        assertEquals(2, attributes.size)
        assertEquals(userSessionId, attributes[EmbSessionAttributes.EMB_USER_SESSION_ID])
        assertEquals("exceptionValue", attributes[ExceptionAttributes.EXCEPTION_TYPE])
        assertEquals(userSessionId, telemetryAttributes.getAttribute(EmbSessionAttributes.EMB_USER_SESSION_ID))
        assertEquals("exceptionValue", telemetryAttributes.getAttribute(ExceptionAttributes.EXCEPTION_TYPE))
    }

    @Test
    fun `all attributes types`() {
        telemetryAttributes = TelemetryAttributes(
            customAttributes = customAttributes,
        )
        val userSessionIdKey = EmbSessionAttributes.EMB_USER_SESSION_ID
        telemetryAttributes.setAttribute(userSessionIdKey, userSessionId)

        val attributes = telemetryAttributes.snapshot()
        assertEquals("attributeValue", attributes["custom"])
        assertEquals(userSessionId, attributes[userSessionIdKey])
    }

    @Test
    fun `overwritten values returned`() {
        val newUserSessionId = TestUuidSource().createUuid()
        telemetryAttributes = TelemetryAttributes()
        val userSessionIdKey = EmbSessionAttributes.EMB_USER_SESSION_ID
        telemetryAttributes.setAttribute(userSessionIdKey, userSessionId)
        telemetryAttributes.setAttribute(userSessionIdKey, newUserSessionId)

        val attributes = telemetryAttributes.snapshot()
        assertEquals(1, attributes.size)
        assertEquals(newUserSessionId, attributes[userSessionIdKey])
    }

    @Test
    fun `schema attribute values take priority if the same key is used`() {
        val newUserSessionId = TestUuidSource().createUuid()
        telemetryAttributes = TelemetryAttributes(
            customAttributes = mapOf(EmbSessionAttributes.EMB_USER_SESSION_ID to userSessionId),
        )
        telemetryAttributes.setAttribute(EmbSessionAttributes.EMB_USER_SESSION_ID, newUserSessionId)
        val attributes = telemetryAttributes.snapshot()
        assertEquals(1, attributes.size)
        assertEquals(newUserSessionId, attributes[EmbSessionAttributes.EMB_USER_SESSION_ID])
    }

    @Test
    fun `log properties and session properties are included in the attributes`() {
        telemetryAttributes = TelemetryAttributes(
            customAttributes = customAttributes,
        )
        telemetryAttributes.setAttribute(EmbSessionAttributes.EMB_USER_SESSION_ID, userSessionId)

        val attributes = telemetryAttributes.snapshot()
        assertEquals(2, attributes.size)
    }

    @Test
    fun `blankish values skipped when directed to do so`() {
        telemetryAttributes = TelemetryAttributes()
        val blankishValues = listOf("", " ", "null", "NULL")

        // Give me Union types, plz
        val userSessionIdKey = EmbSessionAttributes.EMB_USER_SESSION_ID
        blankishValues.forEach { value ->
            telemetryAttributes.setAttribute(userSessionIdKey, value, true)
            assertEquals(value, telemetryAttributes.getAttribute(userSessionIdKey))
        }

        telemetryAttributes.setAttribute(userSessionIdKey, "test")

        blankishValues.forEach { value ->
            telemetryAttributes.setAttribute(userSessionIdKey, value, false)
            assertEquals("test", telemetryAttributes.getAttribute(userSessionIdKey))
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
