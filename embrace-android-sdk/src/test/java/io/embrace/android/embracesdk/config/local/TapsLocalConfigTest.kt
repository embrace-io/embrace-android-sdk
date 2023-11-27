package io.embrace.android.embracesdk.config.local

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

internal class TapsLocalConfigTest {

    private val serializer = EmbraceSerializer()

    @Test
    fun testDefaults() {
        val cfg = TapsLocalConfig()
        assertNull(cfg.captureCoordinates)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("taps_config.json")
        val obj = serializer.fromJson(json, TapsLocalConfig::class.java)
        assertFalse(checkNotNull(obj.captureCoordinates))
    }

    @Test
    fun testEmptyObject() {
        val obj = serializer.fromJson("{}", TapsLocalConfig::class.java)
        assertNull(obj.captureCoordinates)
    }
}
