package io.embrace.android.embracesdk.session.properties

import io.embrace.android.embracesdk.FakeNdkService
import io.embrace.android.embracesdk.arch.destination.SpanAttributeData
import io.embrace.android.embracesdk.capture.session.SessionPropertiesDataSource
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import org.junit.Assert.assertEquals
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
        service = EmbraceSessionPropertiesService(ndkService, props, dataSource)
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
}
