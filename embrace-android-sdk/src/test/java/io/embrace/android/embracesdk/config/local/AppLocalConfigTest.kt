package io.embrace.android.embracesdk.config.local

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

internal class AppLocalConfigTest {

    private val serializer = EmbraceSerializer()

    @Test
    fun testDefaults() {
        val cfg = AppLocalConfig()
        assertNull(cfg.reportDiskUsage)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("app_config.json")
        val obj = serializer.fromJson(json, AppLocalConfig::class.java)
        assertFalse(checkNotNull(obj.reportDiskUsage))
    }

    @Test
    fun testEmptyObject() {
        val obj = serializer.fromJson("{}", AppLocalConfig::class.java)
        assertNull(obj.reportDiskUsage)
    }
}
