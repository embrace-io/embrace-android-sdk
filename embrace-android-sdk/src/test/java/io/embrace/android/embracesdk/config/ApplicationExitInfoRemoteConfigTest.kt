package io.embrace.android.embracesdk.config

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.config.behavior.AppExitInfoBehavior.Companion.AEI_MAX_NUM_DEFAULT
import io.embrace.android.embracesdk.config.remote.AppExitInfoConfig
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class ApplicationExitInfoRemoteConfigTest {

    private val serializer = EmbraceSerializer()

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
        val data = ResourceReader.readResourceAsText("application_exit_info_remote_config.json")
        val appExitInfoConfig = serializer.fromJson(data, AppExitInfoConfig::class.java)
        assertEquals(100, appExitInfoConfig.appExitInfoTracesLimit)
        assertEquals(100f, appExitInfoConfig.pctAeiCaptureEnabled)
        assertEquals(50, appExitInfoConfig.aeiMaxNum)
    }

    @Test
    fun testEmptyObject() {
        val appExitInfoConfig = serializer.fromJson("{}", AppExitInfoConfig::class.java)
        assertNull(appExitInfoConfig.appExitInfoTracesLimit)
        assertNull(appExitInfoConfig.pctAeiCaptureEnabled)
        assertEquals(AEI_MAX_NUM_DEFAULT, appExitInfoConfig.aeiMaxNum)
    }
}
