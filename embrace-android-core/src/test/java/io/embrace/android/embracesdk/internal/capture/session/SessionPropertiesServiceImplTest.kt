package io.embrace.android.embracesdk.internal.capture.session

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class SessionPropertiesServiceImplTest {

    private lateinit var service: SessionPropertiesService
    private lateinit var fakeCurrentSessionSpan: FakeCurrentSessionSpan

    @Before
    fun setUp() {
        fakeCurrentSessionSpan = FakeCurrentSessionSpan()
        service = SessionPropertiesServiceImpl(
            FakePreferenceService(),
            FakeConfigService(),
            FakeEmbLogger(),
            fakeCurrentSessionSpan
        )
    }

    @Test
    fun testAddSessionProp() {
        service.addProperty("key", "value", false)
        val expected = mapOf("key" to "value")
        assertEquals(expected, service.getProperties())
        assertEquals(1, fakeCurrentSessionSpan.attributeCount())

        service.removeProperty("key")
        assertEquals(0, fakeCurrentSessionSpan.attributeCount())
    }

    @Test
    fun `populate session span with all set properties`() {
        service = SessionPropertiesServiceImpl(
            FakePreferenceService().apply { permanentSessionProperties = mapOf("key" to "value") },
            FakeConfigService(),
            FakeEmbLogger(),
            fakeCurrentSessionSpan
        )
        assertEquals(0, fakeCurrentSessionSpan.attributeCount())
        service.addProperty("temp", "value", false)
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
