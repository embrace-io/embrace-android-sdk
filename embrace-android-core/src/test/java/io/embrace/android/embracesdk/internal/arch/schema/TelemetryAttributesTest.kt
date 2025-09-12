package io.embrace.android.embracesdk.internal.arch.schema

import io.embrace.android.embracesdk.fakes.FakeSessionPropertiesService
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.otel.attrs.embProcessIdentifier
import io.embrace.android.embracesdk.internal.session.getSessionProperty
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.opentelemetry.kotlin.semconv.ExceptionAttributes
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.semconv.SessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(IncubatingApi::class)
internal class TelemetryAttributesTest {

    private lateinit var customAttributes: Map<String, String>
    private lateinit var sessionPropertiesService: SessionPropertiesService
    private lateinit var telemetryAttributes: TelemetryAttributes
    private lateinit var sessionId: String

    @Before
    fun setup() {
        customAttributes = mapOf("custom" to "attributeValue")
        sessionPropertiesService = FakeSessionPropertiesService()
        sessionId = Uuid.getEmbUuid()
    }

    @Test
    fun `only schema properties`() {
        telemetryAttributes = TelemetryAttributes()
        telemetryAttributes.setAttribute(SessionAttributes.SESSION_ID, sessionId)
        telemetryAttributes.setAttribute(ExceptionAttributes.EXCEPTION_TYPE, "exceptionValue")
        val attributes = telemetryAttributes.snapshot()
        assertEquals(2, attributes.size)
        assertEquals(sessionId, attributes[SessionAttributes.SESSION_ID])
        assertEquals("exceptionValue", attributes[ExceptionAttributes.EXCEPTION_TYPE])
        assertEquals(sessionId, telemetryAttributes.getAttribute(SessionAttributes.SESSION_ID))
        assertEquals("exceptionValue", telemetryAttributes.getAttribute(ExceptionAttributes.EXCEPTION_TYPE))
    }

    @Test
    fun `all attributes types`() {
        telemetryAttributes = TelemetryAttributes(
            sessionPropertiesProvider = sessionPropertiesService::getProperties,
            customAttributes = customAttributes
        )
        val sessionIdKey = SessionAttributes.SESSION_ID
        telemetryAttributes.setAttribute(sessionIdKey, sessionId)
        sessionPropertiesService.addProperty("perm", "permVal", true)
        sessionPropertiesService.addProperty("temp", "tempVal", false)

        val attributes = telemetryAttributes.snapshot()
        assertEquals("attributeValue", attributes["custom"])
        assertEquals("permVal", attributes.getSessionProperty("perm"))
        assertEquals("tempVal", attributes.getSessionProperty("temp"))
        assertEquals(sessionId, attributes[sessionIdKey])
        sessionPropertiesService.addProperty("temp", "newVal", false)
        assertEquals("newVal", telemetryAttributes.snapshot().getSessionProperty("temp"))
    }

    @Test
    fun `overwritten values returned`() {
        val newSessionId = Uuid.getEmbUuid()
        telemetryAttributes = TelemetryAttributes(
            sessionPropertiesProvider = sessionPropertiesService::getProperties,
        )
        val sessionIdKey = SessionAttributes.SESSION_ID
        telemetryAttributes.setAttribute(sessionIdKey, sessionId)
        telemetryAttributes.setAttribute(sessionIdKey, newSessionId)
        sessionPropertiesService.addProperty("perm", "permVal", true)
        sessionPropertiesService.addProperty("temp", "tempVal", false)
        sessionPropertiesService.addProperty("perm", "newPermVal", true)
        sessionPropertiesService.addProperty("temp", "newTempVal", false)

        val attributes = telemetryAttributes.snapshot()
        assertEquals(3, attributes.size)
        assertEquals("newPermVal", attributes.getSessionProperty("perm"))
        assertEquals("newTempVal", attributes.getSessionProperty("temp"))
        assertEquals(newSessionId, attributes[sessionIdKey])
    }

    @Test
    fun `schema attribute values take priority if the same key is used`() {
        val newSessionId = Uuid.getEmbUuid()
        telemetryAttributes = TelemetryAttributes(
            customAttributes = mapOf(SessionAttributes.SESSION_ID to sessionId)
        )
        telemetryAttributes.setAttribute(SessionAttributes.SESSION_ID, newSessionId)
        val attributes = telemetryAttributes.snapshot()
        assertEquals(1, attributes.size)
        assertEquals(newSessionId, attributes[SessionAttributes.SESSION_ID])
    }

    @Test
    fun `log properties and session properties are included in the attributes`() {
        sessionPropertiesService.addProperty("perm", "permVal", true)
        sessionPropertiesService.addProperty("temp", "tempVal", false)

        telemetryAttributes = TelemetryAttributes(
            sessionPropertiesProvider = sessionPropertiesService::getProperties,
            customAttributes = customAttributes
        )
        telemetryAttributes.setAttribute(SessionAttributes.SESSION_ID, sessionId)

        val attributes = telemetryAttributes.snapshot()
        assertEquals(4, attributes.size)
    }

    @Test
    fun `blankish values skipped when directed to do so`() {
        telemetryAttributes = TelemetryAttributes()
        val blankishValues = listOf("", " ", "null", "NULL")

        // Give me Union types, plz
        val sessionIdKey = SessionAttributes.SESSION_ID
        blankishValues.forEach { value ->
            telemetryAttributes.setAttribute(sessionIdKey, value, true)
            assertEquals(value, telemetryAttributes.getAttribute(sessionIdKey))
        }

        telemetryAttributes.setAttribute(sessionIdKey, "test")

        blankishValues.forEach { value ->
            telemetryAttributes.setAttribute(sessionIdKey, value, false)
            assertEquals("test", telemetryAttributes.getAttribute(sessionIdKey))
        }

        blankishValues.forEach { value ->
            telemetryAttributes.setAttribute(embProcessIdentifier, value, true)
            assertEquals(value, telemetryAttributes.getAttribute(embProcessIdentifier))
        }

        telemetryAttributes.setAttribute(embProcessIdentifier, "test")

        blankishValues.forEach { value ->
            telemetryAttributes.setAttribute(embProcessIdentifier, value, false)
            assertEquals("test", telemetryAttributes.getAttribute(embProcessIdentifier))
        }
    }
}
