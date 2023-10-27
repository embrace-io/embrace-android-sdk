package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.capture.orientation.EmbraceOrientationService
import io.embrace.android.embracesdk.internal.clock.Clock
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class EmbraceOrientationServiceTest {

    lateinit var service: EmbraceOrientationService
    private val clock = Clock { 1509234234L }

    @Before
    fun setUp() {
        service = EmbraceOrientationService(clock).apply {
            onOrientationChanged(1) // portrait orientation
        }
    }

    @Test
    fun testDataCapture() {
        val orientation = service.getCapturedData().single()
        assertEquals("p", orientation.orientation)
        assertEquals(1, orientation.internalOrientation)
        assertEquals(clock.now(), orientation.timestamp)
    }

    @Test
    fun testCleanCollections() {
        assertEquals(1, service.getCapturedData().size)
        service.cleanCollections()
        assertEquals(0, service.getCapturedData().size)
    }

    @Test
    fun testMissingOrientation() {
        assertEquals(1, service.getCapturedData().size)
        service.onOrientationChanged(null)
        assertEquals(1, service.getCapturedData().size)
    }

    @Test
    fun testMatchesLastOrientation() {
        assertEquals(1, service.getCapturedData().size)
        service.onOrientationChanged(1)
        assertEquals(1, service.getCapturedData().size)
    }
}
