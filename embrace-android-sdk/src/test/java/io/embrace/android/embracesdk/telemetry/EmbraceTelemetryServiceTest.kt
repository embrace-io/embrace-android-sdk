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
        assertEquals(null, embraceTelemetryService.usageCountMap["testPublicApi"])

        // When the method is added
        embraceTelemetryService.onPublicApiCalled("testPublicApi")

        // Then the method is in the map
        assertEquals(1, embraceTelemetryService.usageCountMap["testPublicApi"])
    }

    @Test
    fun `onPublicApiCalled with an existing name`() {
        // Given a method is in the map
        embraceTelemetryService.onPublicApiCalled("aMethod")

        // When the same method is called again
        embraceTelemetryService.onPublicApiCalled("aMethod")

        // Then the method is counted twice
        assertEquals(2, embraceTelemetryService.usageCountMap["aMethod"])
    }

    @Test
    fun `onSessionEnd clears the map`() {
        // Given a method is in the map
        embraceTelemetryService.onPublicApiCalled("aMethod")

        // When the session ends
        embraceTelemetryService.onSessionEnd()

        // Then the map is empty
        assertEquals(0, embraceTelemetryService.usageCountMap.size)
    }
}
