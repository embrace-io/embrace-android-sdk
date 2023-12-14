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
        assertEquals(null, embraceTelemetryService.getAndClearTelemetryAttributes()["emb.usage.test_public_api"])

        // When the method is added
        embraceTelemetryService.onPublicApiCalled("test_public_api")

        // Then the method is in the map
        assertEquals("1", embraceTelemetryService.getAndClearTelemetryAttributes()["emb.usage.test_public_api"])
    }

    @Test
    fun `onPublicApiCalled with an existing name`() {
        // Given a method is in the map
        embraceTelemetryService.onPublicApiCalled("public_api")

        // When the same method is called again
        embraceTelemetryService.onPublicApiCalled("public_api")

        // Then the method is counted twice
        assertEquals("2", embraceTelemetryService.getAndClearTelemetryAttributes()["emb.usage.public_api"])
    }

    @Test
    fun `getTelemetryAttributes clears the usage map`() {
        // Given a method is in the map
        embraceTelemetryService.onPublicApiCalled("a_method")

        // When getting telemetry attributes
        embraceTelemetryService.getAndClearTelemetryAttributes()

        // That method isn't in the map anymore
        assertEquals(null, embraceTelemetryService.getAndClearTelemetryAttributes().getOrDefault("emb.usage.a_method", null))
    }
}
