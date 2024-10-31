package io.embrace.android.embracesdk.internal.telemetry

import io.embrace.android.embracesdk.internal.SystemInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EmbraceTelemetryServiceTest {

    private lateinit var embraceTelemetryService: TelemetryService

    @Before
    fun setUp() {
        embraceTelemetryService = EmbraceTelemetryService(systemInfo = SystemInfo())
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
    fun `getTelemetryAttributes clears maps`() {
        // Given a method is in the map
        embraceTelemetryService.onPublicApiCalled("a_method")
        embraceTelemetryService.logStorageTelemetry(mapOf("emb.storage.used" to "12"))

        // After getting telemetry attributes
        embraceTelemetryService.getAndClearTelemetryAttributes()
        val attributes = embraceTelemetryService.getAndClearTelemetryAttributes()

        // That method isn't in the map anymore
        assertEquals(null, attributes.getOrDefault("emb.usage.a_method", null))
        assertEquals(null, attributes.getOrDefault("emb.storage.used", null))
    }

    @Test
    fun `logStorageTelemetry adds usage to the telemetry attributes`() {
        // Given storage telemetry is added
        embraceTelemetryService.logStorageTelemetry(mapOf("emb.storage.used" to "1231"))

        // When getting telemetry attributes
        val telemetryAttributes = embraceTelemetryService.getAndClearTelemetryAttributes()

        // Then storage telemetry is in the map
        assertEquals("1231", telemetryAttributes["emb.storage.used"])
    }

    @Test
    fun `getTelemetryAttributes clears the storage map`() {
        // Given a method is in the map
        embraceTelemetryService.logStorageTelemetry(mapOf("emb.storage.used" to "1"))

        // When getting telemetry attributes
        embraceTelemetryService.getAndClearTelemetryAttributes()

        // That method isn't in the map anymore
        assertEquals(
            null,
            embraceTelemetryService.getAndClearTelemetryAttributes().getOrDefault("emb.storage.a_file", null)
        )
    }

    @Test
    fun `getTelemetryAttributes adds app attributes`() {
        // When getting telemetry attributes
        val telemetryAttributes = embraceTelemetryService.getAndClearTelemetryAttributes()

        // Then the app attributes are in the map
        assertEquals("true", telemetryAttributes["emb.okhttp3"])
        assertTrue(telemetryAttributes.containsKey("emb.okhttp3_on_classpath"))
        assertTrue(telemetryAttributes.containsKey("emb.is_emulator"))
        assertTrue(telemetryAttributes.containsKey("emb.kotlin_on_classpath"))
    }

    @Test
    fun `usage, storage and app attributes are added correctly`() {
        // Given some usage and storage attributes are added
        embraceTelemetryService.onPublicApiCalled("a_method")
        embraceTelemetryService.logStorageTelemetry(mapOf("emb.storage.used" to "12"))

        // When getting telemetry attributes
        val telemetryAttributes = embraceTelemetryService.getAndClearTelemetryAttributes()

        // Then the usage, storage, and app attributes are in the map
        assertEquals("1", telemetryAttributes["emb.usage.a_method"])
        assertEquals("12", telemetryAttributes["emb.storage.used"])
        assertTrue(telemetryAttributes.containsKey("emb.okhttp3"))
        assertTrue(telemetryAttributes.containsKey("emb.okhttp3_on_classpath"))
    }
}
