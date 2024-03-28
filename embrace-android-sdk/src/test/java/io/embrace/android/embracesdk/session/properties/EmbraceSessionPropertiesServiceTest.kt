package io.embrace.android.embracesdk.session.properties

import io.embrace.android.embracesdk.FakeNdkService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class EmbraceSessionPropertiesServiceTest {

    private lateinit var service: SessionPropertiesService
    private lateinit var props: EmbraceSessionProperties
    private lateinit var ndkService: FakeNdkService

    @Before
    fun setUp() {
        val logger = InternalEmbraceLogger()
        props = EmbraceSessionProperties(FakePreferenceService(), FakeConfigService(), logger)
        ndkService = FakeNdkService()
        service = EmbraceSessionPropertiesService(ndkService, props)
    }

    @Test
    fun testAddSessionProp() {
        service.addProperty("key", "value", false)
        val expected = mapOf("key" to "value")
        assertEquals(expected, props.get())
        assertEquals(expected, ndkService.propUpdates.single())
        assertEquals(expected, service.getProperties())

        service.removeProperty("key")
        assertEquals(emptyMap<String, String>(), props.get())
        assertEquals(emptyMap<String, String>(), ndkService.propUpdates.last())
    }
}
