package io.embrace.android.embracesdk.arch.schema

import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.fakeSessionBehavior
import io.embrace.android.embracesdk.internal.spans.getSessionProperty
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.opentelemetry.embSessionId
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import io.opentelemetry.semconv.incubating.ExceptionIncubatingAttributes
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class TelemetryAttributesTest {

    private lateinit var customAttributes: Map<String, String>
    private lateinit var sessionProperties: EmbraceSessionProperties
    private lateinit var telemetryAttributes: TelemetryAttributes
    private lateinit var sessionId: String
    private lateinit var configService: ConfigService

    @Before
    fun setup() {
        customAttributes = mapOf("custom" to "attributeValue")
        sessionProperties = EmbraceSessionProperties(
            FakePreferenceService(),
            FakeConfigService(),
            EmbLoggerImpl()
        )
        sessionId = Uuid.getEmbUuid()
        configService = FakeConfigService()
    }

    @Test
    fun `only schema properties`() {
        telemetryAttributes = TelemetryAttributes(
            configService = configService,
        )
        telemetryAttributes.setAttribute(embSessionId, sessionId)
        telemetryAttributes.setAttribute(ExceptionIncubatingAttributes.EXCEPTION_TYPE, "exceptionValue")
        val attributes = telemetryAttributes.snapshot()
        assertEquals(2, attributes.size)
        assertEquals(sessionId, attributes[embSessionId.name])
        assertEquals("exceptionValue", attributes[ExceptionIncubatingAttributes.EXCEPTION_TYPE.key])
        assertEquals(sessionId, telemetryAttributes.getAttribute(embSessionId))
        assertEquals("exceptionValue", telemetryAttributes.getAttribute(ExceptionIncubatingAttributes.EXCEPTION_TYPE))
    }

    @Test
    fun `all attributes types`() {
        telemetryAttributes = TelemetryAttributes(
            configService = configService,
            sessionPropertiesProvider = sessionProperties::get,
            customAttributes = customAttributes
        )
        telemetryAttributes.setAttribute(embSessionId, sessionId)
        sessionProperties.add("perm", "permVal", true)
        sessionProperties.add("temp", "tempVal", false)

        val attributes = telemetryAttributes.snapshot()
        assertEquals("attributeValue", attributes["custom"])
        assertEquals("permVal", attributes.getSessionProperty("perm"))
        assertEquals("tempVal", attributes.getSessionProperty("temp"))
        assertEquals(sessionId, attributes[embSessionId.name])
        sessionProperties.add("temp", "newVal", false)
        assertEquals("newVal", telemetryAttributes.snapshot().getSessionProperty("temp"))
    }

    @Test
    fun `overwritten values returned`() {
        val newSessionId = Uuid.getEmbUuid()
        telemetryAttributes = TelemetryAttributes(
            configService = configService,
            sessionPropertiesProvider = sessionProperties::get,
        )
        telemetryAttributes.setAttribute(embSessionId, sessionId)
        telemetryAttributes.setAttribute(embSessionId, newSessionId)
        sessionProperties.add("perm", "permVal", true)
        sessionProperties.add("temp", "tempVal", false)
        sessionProperties.add("perm", "newPermVal", true)
        sessionProperties.add("temp", "newTempVal", false)

        val attributes = telemetryAttributes.snapshot()
        assertEquals(3, attributes.size)
        assertEquals("newPermVal", attributes.getSessionProperty("perm"))
        assertEquals("newTempVal", attributes.getSessionProperty("temp"))
        assertEquals(newSessionId, attributes[embSessionId.name])
    }

    @Test
    fun `schema attribute values take priority if the same key is used`() {
        val newSessionId = Uuid.getEmbUuid()
        telemetryAttributes = TelemetryAttributes(
            configService = configService,
            customAttributes = mapOf(embSessionId.name to sessionId)
        )
        telemetryAttributes.setAttribute(embSessionId, newSessionId)
        val attributes = telemetryAttributes.snapshot()
        assertEquals(1, attributes.size)
        assertEquals(newSessionId, attributes[embSessionId.name])
    }

    @Test
    fun `log properties and session properties are not included in the attributes`() {
        val configService = FakeConfigService(
            sessionBehavior = fakeSessionBehavior(
                remoteCfg = {
                    RemoteConfig(
                        sessionConfig = SessionRemoteConfig(
                            fullSessionEvents = setOf(),
                            sessionComponents = setOf()
                        )
                    )
                }
            )
        )
        sessionProperties.add("perm", "permVal", true)
        sessionProperties.add("temp", "tempVal", false)

        telemetryAttributes = TelemetryAttributes(
            configService = configService,
            sessionPropertiesProvider = sessionProperties::get,
            customAttributes = customAttributes
        )
        telemetryAttributes.setAttribute(embSessionId, sessionId)

        val attributes = telemetryAttributes.snapshot()
        assertEquals(1, attributes.size)
    }

    @Test
    fun `log properties and session properties are included in the attributes`() {
        val configService = FakeConfigService(
            sessionBehavior = fakeSessionBehavior(
                remoteCfg = {
                    RemoteConfig(
                        sessionConfig = SessionRemoteConfig(
                            fullSessionEvents = setOf(),
                            sessionComponents = setOf("s_props", "log_pr")
                        )
                    )
                }
            )
        )
        sessionProperties.add("perm", "permVal", true)
        sessionProperties.add("temp", "tempVal", false)

        telemetryAttributes = TelemetryAttributes(
            configService = configService,
            sessionPropertiesProvider = sessionProperties::get,
            customAttributes = customAttributes
        )
        telemetryAttributes.setAttribute(embSessionId, sessionId)

        val attributes = telemetryAttributes.snapshot()
        assertEquals(4, attributes.size)
    }
}
