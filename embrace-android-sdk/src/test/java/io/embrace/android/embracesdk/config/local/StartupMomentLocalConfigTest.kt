package io.embrace.android.embracesdk.config.local

import com.google.gson.Gson
import io.embrace.android.embracesdk.ResourceReader
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

internal class StartupMomentLocalConfigTest {

    @Test
    fun testDefaults() {
        val cfg = StartupMomentLocalConfig()
        assertNull(cfg.automaticallyEnd)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("startup_moment_config.json")
        val obj = Gson().fromJson(json, StartupMomentLocalConfig::class.java)
        assertFalse(checkNotNull(obj.automaticallyEnd))
    }

    @Test
    fun testEmptyObject() {
        val obj = Gson().fromJson("{}", StartupMomentLocalConfig::class.java)
        assertNull(obj.automaticallyEnd)
    }
}
