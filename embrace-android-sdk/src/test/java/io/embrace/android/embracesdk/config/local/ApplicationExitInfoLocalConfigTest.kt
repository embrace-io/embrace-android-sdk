package io.embrace.android.embracesdk.config.local

import com.google.gson.Gson
import io.embrace.android.embracesdk.ResourceReader
import org.junit.Assert
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
        val json = ResourceReader.readResourceAsText("application_exit_info_local_config.json")
        val obj = Gson().fromJson(json, AppExitInfoLocalConfig::class.java)
        Assert.assertEquals(10, obj.appExitInfoTracesLimit)
        assertTrue(obj.aeiCaptureEnabled ?: false)
    }

    @Test
    fun testEmptyObject() {
        val appExitInfoLocalConfig = Gson().fromJson("{}", AppExitInfoLocalConfig::class.java)
        assertNull(appExitInfoLocalConfig.appExitInfoTracesLimit)
        assertNull(appExitInfoLocalConfig.aeiCaptureEnabled)
    }
}
