package io.embrace.android.embracesdk.config

import com.google.gson.Gson
import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.config.behavior.AppExitInfoBehavior.Companion.AEI_MAX_NUM_DEFAULT
import io.embrace.android.embracesdk.config.remote.AppExitInfoConfig
import org.junit.Assert
import org.junit.Test

internal class ApplicationExitInfoRemoteConfigTest {

    @Test
    fun testDefaults() {
        val appExitInfoConfig = AppExitInfoConfig()
        Assert.assertEquals(AEI_MAX_NUM_DEFAULT, appExitInfoConfig.aeiMaxNum)
    }

    @Test
    fun testOverride() {
        val appExitInfoConfig = AppExitInfoConfig(
            100,
            100f,
            50
        )
        Assert.assertEquals(100, appExitInfoConfig.appExitInfoTracesLimit)
        Assert.assertEquals(100f, appExitInfoConfig.pctAeiCaptureEnabled)
        Assert.assertEquals(50, appExitInfoConfig.aeiMaxNum)
    }

    @Test
    fun testDeserialization() {
        val data = ResourceReader.readResourceAsText("application_exit_info_remote_config.json")
        val appExitInfoConfig = Gson().fromJson(data, AppExitInfoConfig::class.java)
        Assert.assertEquals(100, appExitInfoConfig.appExitInfoTracesLimit)
        Assert.assertEquals(100f, appExitInfoConfig.pctAeiCaptureEnabled)
        Assert.assertEquals(50, appExitInfoConfig.aeiMaxNum)
    }

    @Test
    fun testEmptyObject() {
        val appExitInfoConfig = Gson().fromJson("{}", AppExitInfoConfig::class.java)
        Assert.assertNull(appExitInfoConfig.appExitInfoTracesLimit)
        Assert.assertNull(appExitInfoConfig.pctAeiCaptureEnabled)
        Assert.assertEquals(AEI_MAX_NUM_DEFAULT, appExitInfoConfig.aeiMaxNum)
    }
}
