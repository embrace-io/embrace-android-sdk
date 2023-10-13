package io.embrace.android.embracesdk.config.local

import com.google.gson.Gson
import io.embrace.android.embracesdk.ResourceReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class BackgroundActivityLocalConfigTest {

    @Test
    fun testDefaults() {
        val cfg = BackgroundActivityLocalConfig()
        verifyDefaults(cfg)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("background_activity_config.json")
        val obj = Gson().fromJson(json, BackgroundActivityLocalConfig::class.java)
        assertTrue(checkNotNull(obj.backgroundActivityCaptureEnabled))
        assertEquals(15, obj.manualBackgroundActivityLimit)
        assertEquals(10000L, obj.minBackgroundActivityDuration)
        assertEquals(16, obj.maxCachedActivities)
    }

    @Test
    fun testEmptyObject() {
        val obj = Gson().fromJson("{}", BackgroundActivityLocalConfig::class.java)
        verifyDefaults(obj)
    }

    private fun verifyDefaults(cfg: BackgroundActivityLocalConfig) {
        assertNull(cfg.backgroundActivityCaptureEnabled)
        assertNull(cfg.manualBackgroundActivityLimit)
        assertNull(cfg.minBackgroundActivityDuration)
        assertNull(cfg.maxCachedActivities)
    }
}
