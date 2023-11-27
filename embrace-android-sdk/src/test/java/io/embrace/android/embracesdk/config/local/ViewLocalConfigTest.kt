package io.embrace.android.embracesdk.config.local

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

internal class ViewLocalConfigTest {

    private val serializer = EmbraceSerializer()

    @Test
    fun testDefaults() {
        val cfg = ViewLocalConfig()
        assertNull(cfg.enableAutomaticActivityCapture)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("view_config.json")
        val obj = serializer.fromJson(json, ViewLocalConfig::class.java)
        assertFalse(checkNotNull(obj.enableAutomaticActivityCapture))
    }

    @Test
    fun testEmptyObject() {
        val obj = serializer.fromJson("{}", ViewLocalConfig::class.java)
        assertNull(obj.enableAutomaticActivityCapture)
    }
}
