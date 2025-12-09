package io.embrace.android.embracesdk.internal.capture.session

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeRedactionConfig
import io.embrace.android.embracesdk.internal.config.behavior.REDACTED_LABEL
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehaviorImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class SessionPropertiesServiceImplTest {

    private lateinit var service: SessionPropertiesService
    private lateinit var destination: FakeTelemetryDestination
    private lateinit var propState: Map<String, String>

    @Before
    fun setUp() {
        val fakeConfigService =
            FakeConfigService(
                sensitiveKeysBehavior = SensitiveKeysBehaviorImpl(
                    FakeInstrumentedConfig(redaction = FakeRedactionConfig(sensitiveKeys = listOf("password"))),
                )
            )
        destination = FakeTelemetryDestination()
        service = SessionPropertiesServiceImpl(
            FakeKeyValueStore(),
            fakeConfigService,
            destination
        )
        propState = emptyMap()
        service.addChangeListener { propState = it }
    }

    @Test
    fun testAddSessionProp() {
        service.addProperty("key", "value", false)
        val expected = mapOf("key" to "value")
        assertEquals(expected, propState)
        assertEquals(expected, service.getProperties())
        assertEquals(1, destination.attributes.size)

        service.removeProperty("key")
        assertEquals(emptyMap<String, String>(), propState)
        assertEquals(0, destination.attributes.size)
    }

    @Test
    fun testAddRedactedSessionProp() {
        service.addProperty("password", "value", false)
        val expected = mapOf("password" to REDACTED_LABEL)
        assertEquals(expected, service.getProperties())
        assertEquals(1, destination.attributes.size)

        service.removeProperty("password")
        assertEquals(0, destination.attributes.size)
    }

    @Test
    fun `populate session span with all set properties`() {
        assertEquals(0, destination.attributes.size)
        service.addProperty("temp", "value", false)
        service.addProperty("perm", "value", true)
        assertEquals(2, destination.attributes.size)
    }

    @Test
    fun addSessionPropertyInvalidKey() {
        assertFalse(service.addProperty("", "value", false))
        assertTrue(service.getProperties().isEmpty())
    }

    @Test
    fun addSessionPropertyKeyTooLong() {
        val longKey = "a".repeat(129)
        assertTrue(service.addProperty(longKey, "value", false))
        assertEquals(1, service.getProperties().size.toLong())
        val key = "a".repeat(125) + "..."
        assertEquals("value", service.getProperties()[key])
    }

    @Test
    fun addSessionPropertyValueTooLong() {
        val longValue = "a".repeat(1025)
        assertTrue(service.addProperty("key", longValue, false))
        assertEquals(1, service.getProperties().size.toLong())
        val value = "a".repeat(1021) + "..."
        assertEquals(value, service.getProperties()["key"])
    }
}
