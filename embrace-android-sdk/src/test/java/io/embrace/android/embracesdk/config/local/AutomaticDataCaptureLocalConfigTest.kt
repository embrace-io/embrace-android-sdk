package io.embrace.android.embracesdk.config.local

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

internal class AutomaticDataCaptureLocalConfigTest {

    private val serializer = EmbraceSerializer()

    @Test
    fun testDefaults() {
        val cfg = AutomaticDataCaptureLocalConfig()
        verifyDefaults(cfg)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("auto_data_capture_config.json")
        val obj = serializer.fromJson(json, AutomaticDataCaptureLocalConfig::class.java)

        assertFalse(checkNotNull(obj.anrServiceEnabled))
        assertFalse(checkNotNull(obj.memoryServiceEnabled))
        assertFalse(checkNotNull(obj.networkConnectivityServiceEnabled))
        assertFalse(checkNotNull(obj.powerSaveModeServiceEnabled))
    }

    @Test
    fun testEmptyObject() {
        val obj = serializer.fromJson("{}", AutomaticDataCaptureLocalConfig::class.java)
        verifyDefaults(obj)
    }

    private fun verifyDefaults(cfg: AutomaticDataCaptureLocalConfig) {
        assertNull(cfg.anrServiceEnabled)
        assertNull(cfg.memoryServiceEnabled)
        assertNull(cfg.networkConnectivityServiceEnabled)
        assertNull(cfg.powerSaveModeServiceEnabled)
    }
}
