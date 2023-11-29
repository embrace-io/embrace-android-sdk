package io.embrace.android.embracesdk.config.local

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class AnrLocalConfigTest {

    private val serializer = EmbraceSerializer()

    @Test
    fun testDefaults() {
        val cfg = AnrLocalConfig()
        assertNull(cfg.captureGoogle)
        assertNull(cfg.captureUnityThread)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("anr_config.json")
        val obj = serializer.fromJson(json, AnrLocalConfig::class.java)
        assertTrue(checkNotNull(obj.captureGoogle))
        assertTrue(checkNotNull(obj.captureUnityThread))
    }

    @Test
    fun testEmptyObject() {
        val obj = serializer.fromJson("{}", AnrLocalConfig::class.java)
        assertNull(obj.captureGoogle)
        assertNull(obj.captureUnityThread)
    }
}
