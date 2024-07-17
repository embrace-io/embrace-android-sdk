package io.embrace.android.embracesdk.config.local

import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import io.embrace.android.embracesdk.internal.config.local.AutomaticDataCaptureLocalConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

internal class AutomaticDataCaptureLocalConfigTest {

    @Test
    fun testDefaults() {
        val cfg = AutomaticDataCaptureLocalConfig()
        verifyDefaults(cfg)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<AutomaticDataCaptureLocalConfig>("auto_data_capture_config.json")
        assertFalse(checkNotNull(obj.anrServiceEnabled))
        assertFalse(checkNotNull(obj.memoryServiceEnabled))
        assertFalse(checkNotNull(obj.networkConnectivityServiceEnabled))
        assertFalse(checkNotNull(obj.powerSaveModeServiceEnabled))
    }

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<AutomaticDataCaptureLocalConfig>()
        assertNotNull(obj)
    }

    private fun verifyDefaults(cfg: AutomaticDataCaptureLocalConfig) {
        assertNull(cfg.anrServiceEnabled)
        assertNull(cfg.memoryServiceEnabled)
        assertNull(cfg.networkConnectivityServiceEnabled)
        assertNull(cfg.powerSaveModeServiceEnabled)
    }
}
