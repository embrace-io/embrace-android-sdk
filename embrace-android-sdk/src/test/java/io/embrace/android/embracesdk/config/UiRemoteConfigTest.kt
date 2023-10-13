package io.embrace.android.embracesdk.config

import com.google.gson.Gson
import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.config.remote.UiRemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class UiRemoteConfigTest {

    @Test
    fun testDefaults() {
        val cfg = UiRemoteConfig(null, null, null, null, null)
        verifyDefaults(cfg)
    }

    @Test
    fun testOverride() {
        val cfg = UiRemoteConfig(
            100,
            50,
            200,
            500,
            300
        )
        assertEquals(100, cfg.breadcrumbs)
        assertEquals(50, cfg.taps)
        assertEquals(200, cfg.views)
        assertEquals(500, cfg.webViews)
        assertEquals(300, cfg.fragments)
    }

    @Test
    fun testDeserialization() {
        val data = ResourceReader.readResourceAsText("ui_config.json")
        val cfg = Gson().fromJson(data, UiRemoteConfig::class.java)
        assertEquals(80, cfg.breadcrumbs)
        assertEquals(50, cfg.taps)
        assertEquals(200, cfg.views)
        assertEquals(500, cfg.webViews)
        assertEquals(300, cfg.fragments)
    }

    @Test
    fun testDeserializationEmptyObj() {
        val cfg = Gson().fromJson("{}", UiRemoteConfig::class.java)
        verifyDefaults(cfg)
    }

    private fun verifyDefaults(cfg: UiRemoteConfig) {
        assertNull(cfg.breadcrumbs)
        assertNull(cfg.taps)
        assertNull(cfg.views)
        assertNull(cfg.webViews)
        assertNull(cfg.fragments)
    }
}
