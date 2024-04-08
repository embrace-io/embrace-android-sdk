package io.embrace.android.embracesdk.arch.schema

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.opentelemetry.embSessionId
import io.embrace.android.embracesdk.opentelemetry.exceptionType
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class TelemetryAttributesTest {

    private lateinit var customAttributes: Map<String, String>
    private lateinit var sessionProperties: EmbraceSessionProperties
    private lateinit var telemetryAttributes: TelemetryAttributes
    private lateinit var sessionId: String

    @Before
    fun setup() {
        customAttributes = mapOf("custom" to "attributeValue")
        sessionProperties = EmbraceSessionProperties(
            FakePreferenceService(),
            FakeConfigService(),
            InternalEmbraceLogger()
        )
        sessionId = Uuid.getEmbUuid()
    }

    @Test
    fun `only schema properties`() {
        telemetryAttributes = TelemetryAttributes()
        telemetryAttributes.setAttribute(embSessionId, sessionId)
        telemetryAttributes.setAttribute(exceptionType, "exceptionValue")
        val attributes = telemetryAttributes.snapshot()
        assertEquals(2, attributes.size)
        assertEquals(sessionId, attributes[embSessionId.name])
        assertEquals("exceptionValue", attributes[exceptionType.key])
    }

    @Test
    fun `all attributes types`() {
        telemetryAttributes = TelemetryAttributes(
            sessionProperties = sessionProperties,
            customAttributes = customAttributes
        )
        telemetryAttributes.setAttribute(embSessionId, sessionId)
        sessionProperties.add("perm", "permVal", true)
        sessionProperties.add("temp", "tempVal", false)

        val attributes = telemetryAttributes.snapshot()
        assertEquals("attributeValue", attributes["custom"])
        assertEquals("permVal", attributes["emb.properties.perm"])
        assertEquals("tempVal", attributes["emb.properties.temp"])
        assertEquals(sessionId, attributes[embSessionId.name])
        sessionProperties.add("temp", "newVal", false)
        assertEquals("newVal", telemetryAttributes.snapshot()["emb.properties.temp"])
    }

    @Test
    fun `overwritten values returned`() {
        val newSessionId = Uuid.getEmbUuid()
        telemetryAttributes = TelemetryAttributes(
            sessionProperties = sessionProperties
        )
        telemetryAttributes.setAttribute(embSessionId, sessionId)
        telemetryAttributes.setAttribute(embSessionId, newSessionId)
        sessionProperties.add("perm", "permVal", true)
        sessionProperties.add("temp", "tempVal", false)
        sessionProperties.add("perm", "newPermVal", true)
        sessionProperties.add("temp", "newTempVal", false)

        val attributes = telemetryAttributes.snapshot()
        assertEquals(3, attributes.size)
        assertEquals("newPermVal", attributes["emb.properties.perm"])
        assertEquals("newTempVal", attributes["emb.properties.temp"])
        assertEquals(newSessionId, attributes[embSessionId.name])
    }
}
