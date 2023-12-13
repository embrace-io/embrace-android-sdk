package io.embrace.android.embracesdk.telemetry

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class EmbraceTelemetryServiceTest {

    private var embraceTelemetryService = EmbraceTelemetryService()

    @Before
    fun setUp() {
        embraceTelemetryService = EmbraceTelemetryService()
    }

    @Test
    fun `onPublicApiCalled with a new name`() {
        // Given a method is not in the map
        assertEquals(null, embraceTelemetryService.getTelemetryAttributes()["testPublicApi"])

        // When the method is added
        embraceTelemetryService.onPublicApiCalled("testPublicApi")

        // Then the method is in the map
        assertEquals("1", embraceTelemetryService.getTelemetryAttributes()["usage - testPublicApi"])
    }

    @Test
    fun `onPublicApiCalled with an existing name`() {
        // Given a method is in the map
        embraceTelemetryService.onPublicApiCalled("publicApi")

        // When the same method is called again
        embraceTelemetryService.onPublicApiCalled("publicApi")

        // Then the method is counted twice
        assertEquals("2", embraceTelemetryService.getTelemetryAttributes()["usage - publicApi"])
    }

    @Test
    fun `getTelemetryAttributes clears the usage map`() {
        // Given a method is in the map
        embraceTelemetryService.onPublicApiCalled("aMethod")

        // When getting telemetry attributes
        embraceTelemetryService.getTelemetryAttributes()

        // That method isn't in the map anymore
        assertEquals(null, embraceTelemetryService.getTelemetryAttributes().getOrDefault("usage - aMethod", null))
    }
}
