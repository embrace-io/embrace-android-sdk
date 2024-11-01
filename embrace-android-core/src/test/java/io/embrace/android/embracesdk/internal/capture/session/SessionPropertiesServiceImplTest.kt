package io.embrace.android.embracesdk.internal.capture.session

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.internal.config.behavior.REDACTED_LABEL
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehaviorImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class SessionPropertiesServiceImplTest {

    private lateinit var service: SessionPropertiesService
    private lateinit var fakeCurrentSessionSpan: FakeCurrentSessionSpan
    private lateinit var propState: Map<String, String>

    @Before
    fun setUp() {
        val fakeConfigService =
            FakeConfigService(
                sensitiveKeysBehavior = SensitiveKeysBehaviorImpl(listOf("password"))
            )
        fakeCurrentSessionSpan = FakeCurrentSessionSpan()
        service = SessionPropertiesServiceImpl(
            FakePreferenceService(),
            fakeConfigService,
            fakeCurrentSessionSpan
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
        assertEquals(1, fakeCurrentSessionSpan.attributeCount())

        service.removeProperty("key")
        assertEquals(emptyMap<String, String>(), propState)
        assertEquals(0, fakeCurrentSessionSpan.attributeCount())
    }

    @Test
    fun testAddRedactedSessionProp() {
        service.addProperty("password", "value", false)
        val expected = mapOf("password" to REDACTED_LABEL)
        assertEquals(expected, service.getProperties())
        assertEquals(1, fakeCurrentSessionSpan.attributeCount())

        service.removeProperty("password")
        assertEquals(0, fakeCurrentSessionSpan.attributeCount())
    }

    @Test
    fun `populate session span with all set properties`() {
        assertEquals(0, fakeCurrentSessionSpan.attributeCount())
        service.addProperty("temp", "value", false)
        service.addProperty("perm", "value", true)
        assertEquals(2, fakeCurrentSessionSpan.attributeCount())
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
