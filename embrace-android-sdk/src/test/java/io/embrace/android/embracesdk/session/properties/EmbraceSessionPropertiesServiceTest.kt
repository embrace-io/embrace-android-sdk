package io.embrace.android.embracesdk.session.properties

import io.embrace.android.embracesdk.FakeNdkService
import io.embrace.android.embracesdk.arch.destination.SpanAttributeData
import io.embrace.android.embracesdk.capture.session.SessionPropertiesDataSource
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EmbraceSessionPropertiesServiceTest {

    private lateinit var service: SessionPropertiesService
    private lateinit var props: EmbraceSessionProperties
    private lateinit var dataSource: SessionPropertiesDataSource
    private lateinit var ndkService: FakeNdkService
    private lateinit var fakeCurrentSessionSpan: FakeCurrentSessionSpan

    @Before
    fun setUp() {
        val logger = InternalEmbraceLogger()
        val fakeConfigService = FakeConfigService()
        props = EmbraceSessionProperties(FakePreferenceService(), fakeConfigService, logger)
        ndkService = FakeNdkService()
        fakeCurrentSessionSpan = FakeCurrentSessionSpan()
        dataSource = SessionPropertiesDataSource(
            sessionBehavior = fakeConfigService.sessionBehavior,
            writer = fakeCurrentSessionSpan,
            logger = logger,
        )
        service = EmbraceSessionPropertiesService(ndkService, props) { dataSource }
    }

    @Test
    fun testAddSessionProp() {
        service.addProperty("key", "value", false)
        val expected = mapOf("key" to "value")
        assertEquals(expected, props.get())
        assertEquals(expected, ndkService.propUpdates.single())
        assertEquals(expected, service.getProperties())
        assertEquals(SpanAttributeData("key", "value"), fakeCurrentSessionSpan.addedAttributes.first())

        service.removeProperty("key")
        assertEquals(emptyMap<String, String>(), props.get())
        assertEquals(emptyMap<String, String>(), ndkService.propUpdates.last())
        assertTrue(fakeCurrentSessionSpan.addedAttributes.isEmpty())
    }

    @Test
    fun `populate session span with all set properties`() {
        props.add("key", "value", true)
        props.add("tempKey", "tempValue", false)
        assertTrue(fakeCurrentSessionSpan.addedAttributes.isEmpty())
        assertTrue(service.populateCurrentSession())
        assertEquals(2, fakeCurrentSessionSpan.addedAttributes.size)
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
