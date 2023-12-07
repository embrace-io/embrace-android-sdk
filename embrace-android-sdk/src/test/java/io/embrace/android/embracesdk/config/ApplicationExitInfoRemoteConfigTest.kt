package io.embrace.android.embracesdk.config

import io.embrace.android.embracesdk.config.behavior.AppExitInfoBehavior.Companion.AEI_MAX_NUM_DEFAULT
import io.embrace.android.embracesdk.config.remote.AppExitInfoConfig
import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class ApplicationExitInfoRemoteConfigTest {

    @Test
    fun testDefaults() {
        val appExitInfoConfig = AppExitInfoConfig()
        assertEquals(AEI_MAX_NUM_DEFAULT, appExitInfoConfig.aeiMaxNum)
    }

    @Test
    fun testOverride() {
        val appExitInfoConfig = AppExitInfoConfig(
            100,
            100f,
            50
        )
        assertEquals(100, appExitInfoConfig.appExitInfoTracesLimit)
        assertEquals(100f, appExitInfoConfig.pctAeiCaptureEnabled)
        assertEquals(50, appExitInfoConfig.aeiMaxNum)
    }

    @Test
    fun testDeserialization() {
        val appExitInfoConfig = deserializeJsonFromResource<AppExitInfoConfig>("application_exit_info_remote_config.json")
        assertEquals(100, appExitInfoConfig.appExitInfoTracesLimit)
        assertEquals(100f, appExitInfoConfig.pctAeiCaptureEnabled)
        assertEquals(50, appExitInfoConfig.aeiMaxNum)
    }

    @Test
    fun testEmptyObject() {
        val appExitInfoConfig = deserializeEmptyJsonString<AppExitInfoConfig>()
        assertNull(appExitInfoConfig.appExitInfoTracesLimit)
        assertNull(appExitInfoConfig.pctAeiCaptureEnabled)
        assertEquals(AEI_MAX_NUM_DEFAULT, appExitInfoConfig.aeiMaxNum)
    }
}
