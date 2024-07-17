package io.embrace.android.embracesdk.config.local

import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import io.embrace.android.embracesdk.internal.config.local.AppExitInfoLocalConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ApplicationExitInfoLocalConfigTest {

    @Test
    fun testDefaults() {
        val appExitInfoLocalConfig = AppExitInfoLocalConfig()
        assertNull(appExitInfoLocalConfig.appExitInfoTracesLimit)
        assertNull(appExitInfoLocalConfig.aeiCaptureEnabled)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<AppExitInfoLocalConfig>("application_exit_info_local_config.json")
        assertEquals(10, obj.appExitInfoTracesLimit)
        assertTrue(obj.aeiCaptureEnabled ?: false)
    }

    @Test
    fun testEmptyObject() {
        val appExitInfoLocalConfig = deserializeEmptyJsonString<AppExitInfoLocalConfig>()
        assertNull(appExitInfoLocalConfig.appExitInfoTracesLimit)
        assertNull(appExitInfoLocalConfig.aeiCaptureEnabled)
    }
}
