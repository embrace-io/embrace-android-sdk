package io.embrace.android.embracesdk.config.local

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ApplicationExitInfoLocalConfigTest {

    private val serializer = EmbraceSerializer()

    @Test
    fun testDefaults() {
        val appExitInfoLocalConfig = AppExitInfoLocalConfig()
        assertNull(appExitInfoLocalConfig.appExitInfoTracesLimit)
        assertNull(appExitInfoLocalConfig.aeiCaptureEnabled)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("application_exit_info_local_config.json")
        val obj = serializer.fromJson(json, AppExitInfoLocalConfig::class.java)
        assertEquals(10, obj.appExitInfoTracesLimit)
        assertTrue(obj.aeiCaptureEnabled ?: false)
    }

    @Test
    fun testEmptyObject() {
        val appExitInfoLocalConfig = serializer.fromJson("{}", AppExitInfoLocalConfig::class.java)
        assertNull(appExitInfoLocalConfig.appExitInfoTracesLimit)
        assertNull(appExitInfoLocalConfig.aeiCaptureEnabled)
    }
}
