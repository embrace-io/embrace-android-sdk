package io.embrace.android.embracesdk.internal.arch.schema

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeSessionPropertiesService
import io.embrace.android.embracesdk.fakes.createSessionBehavior
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.internal.opentelemetry.embProcessIdentifier
import io.embrace.android.embracesdk.internal.spans.getSessionProperty
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.opentelemetry.semconv.ExceptionAttributes
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class TelemetryAttributesTest {

    private lateinit var customAttributes: Map<String, String>
    private lateinit var sessionPropertiesService: SessionPropertiesService
    private lateinit var telemetryAttributes: TelemetryAttributes
    private lateinit var sessionId: String
    private lateinit var configService: ConfigService

    @Before
    fun setup() {
        customAttributes = mapOf("custom" to "attributeValue")
        sessionPropertiesService = FakeSessionPropertiesService()
        sessionId = Uuid.getEmbUuid()
        configService = FakeConfigService()
    }

    @Test
    fun `only schema properties`() {
        telemetryAttributes = TelemetryAttributes(
            configService = configService,
        )
        telemetryAttributes.setAttribute(SessionIncubatingAttributes.SESSION_ID.key, sessionId)
        telemetryAttributes.setAttribute(ExceptionAttributes.EXCEPTION_TYPE.key, "exceptionValue")
        val attributes = telemetryAttributes.snapshot()
        assertEquals(2, attributes.size)
        assertEquals(sessionId, attributes[SessionIncubatingAttributes.SESSION_ID.key])
        assertEquals("exceptionValue", attributes[ExceptionAttributes.EXCEPTION_TYPE.key])
        assertEquals(sessionId, telemetryAttributes.getAttribute(SessionIncubatingAttributes.SESSION_ID.key))
        assertEquals("exceptionValue", telemetryAttributes.getAttribute(ExceptionAttributes.EXCEPTION_TYPE.key))
    }

    @Test
    fun `all attributes types`() {
        telemetryAttributes = TelemetryAttributes(
            configService = configService,
            sessionPropertiesProvider = sessionPropertiesService::getProperties,
            customAttributes = customAttributes
        )
        telemetryAttributes.setAttribute(SessionIncubatingAttributes.SESSION_ID.key, sessionId)
        sessionPropertiesService.addProperty("perm", "permVal", true)
        sessionPropertiesService.addProperty("temp", "tempVal", false)

        val attributes = telemetryAttributes.snapshot()
        assertEquals("attributeValue", attributes["custom"])
        assertEquals("permVal", attributes.getSessionProperty("perm"))
        assertEquals("tempVal", attributes.getSessionProperty("temp"))
        assertEquals(sessionId, attributes[SessionIncubatingAttributes.SESSION_ID.key])
        sessionPropertiesService.addProperty("temp", "newVal", false)
        assertEquals("newVal", telemetryAttributes.snapshot().getSessionProperty("temp"))
    }

    @Test
    fun `overwritten values returned`() {
        val newSessionId = Uuid.getEmbUuid()
        telemetryAttributes = TelemetryAttributes(
            configService = configService,
            sessionPropertiesProvider = sessionPropertiesService::getProperties,
        )
        telemetryAttributes.setAttribute(SessionIncubatingAttributes.SESSION_ID.key, sessionId)
        telemetryAttributes.setAttribute(SessionIncubatingAttributes.SESSION_ID.key, newSessionId)
        sessionPropertiesService.addProperty("perm", "permVal", true)
        sessionPropertiesService.addProperty("temp", "tempVal", false)
        sessionPropertiesService.addProperty("perm", "newPermVal", true)
        sessionPropertiesService.addProperty("temp", "newTempVal", false)

        val attributes = telemetryAttributes.snapshot()
        assertEquals(3, attributes.size)
        assertEquals("newPermVal", attributes.getSessionProperty("perm"))
        assertEquals("newTempVal", attributes.getSessionProperty("temp"))
        assertEquals(newSessionId, attributes[SessionIncubatingAttributes.SESSION_ID.key])
    }

    @Test
    fun `schema attribute values take priority if the same key is used`() {
        val newSessionId = Uuid.getEmbUuid()
        telemetryAttributes = TelemetryAttributes(
            configService = configService,
            customAttributes = mapOf(SessionIncubatingAttributes.SESSION_ID.key to sessionId)
        )
        telemetryAttributes.setAttribute(SessionIncubatingAttributes.SESSION_ID.key, newSessionId)
        val attributes = telemetryAttributes.snapshot()
        assertEquals(1, attributes.size)
        assertEquals(newSessionId, attributes[SessionIncubatingAttributes.SESSION_ID.key])
    }

    @Test
    fun `log properties and session properties are not included in the attributes`() {
        val configService = FakeConfigService(
            sessionBehavior = createSessionBehavior(
                remoteCfg = RemoteConfig(
                    sessionConfig = SessionRemoteConfig(
                        fullSessionEvents = setOf(),
                        sessionComponents = setOf()
                    )
                )
            )
        )
        sessionPropertiesService.addProperty("perm", "permVal", true)
        sessionPropertiesService.addProperty("temp", "tempVal", false)

        telemetryAttributes = TelemetryAttributes(
            configService = configService,
            sessionPropertiesProvider = sessionPropertiesService::getProperties,
            customAttributes = customAttributes
        )
        telemetryAttributes.setAttribute(SessionIncubatingAttributes.SESSION_ID.key, sessionId)

        val attributes = telemetryAttributes.snapshot()
        assertEquals(1, attributes.size)
    }

    @Test
    fun `log properties and session properties are included in the attributes`() {
        val configService = FakeConfigService(
            sessionBehavior = createSessionBehavior(
                remoteCfg = RemoteConfig(
                    sessionConfig = SessionRemoteConfig(
                        fullSessionEvents = setOf(),
                        sessionComponents = setOf("s_props", "log_pr")
                    )
                )
            )
        )
        sessionPropertiesService.addProperty("perm", "permVal", true)
        sessionPropertiesService.addProperty("temp", "tempVal", false)

        telemetryAttributes = TelemetryAttributes(
            configService = configService,
            sessionPropertiesProvider = sessionPropertiesService::getProperties,
            customAttributes = customAttributes
        )
        telemetryAttributes.setAttribute(SessionIncubatingAttributes.SESSION_ID.key, sessionId)

        val attributes = telemetryAttributes.snapshot()
        assertEquals(4, attributes.size)
    }

    @Test
    fun `blankish values skipped when directed to do so`() {
        telemetryAttributes = TelemetryAttributes(
            configService = configService,
        )
        val blankishValues = listOf("", " ", "null", "NULL")

        // Give me Union types, plz
        blankishValues.forEach { value ->
            telemetryAttributes.setAttribute(SessionIncubatingAttributes.SESSION_ID.key, value, true)
            assertEquals(value, telemetryAttributes.getAttribute(SessionIncubatingAttributes.SESSION_ID.key))
        }

        telemetryAttributes.setAttribute(SessionIncubatingAttributes.SESSION_ID.key, "test")

        blankishValues.forEach { value ->
            telemetryAttributes.setAttribute(SessionIncubatingAttributes.SESSION_ID.key, value, false)
            assertEquals("test", telemetryAttributes.getAttribute(SessionIncubatingAttributes.SESSION_ID.key))
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
