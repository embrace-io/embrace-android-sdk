package io.embrace.android.embracesdk.config.local

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

internal class CrashHandlerLocalConfigTest {

    private val serializer = EmbraceSerializer()

    @Test
    fun testDefaults() {
        val cfg = CrashHandlerLocalConfig()
        assertNull(cfg.enabled)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("crash_handler_config.json")
        val obj = serializer.fromJson(json, CrashHandlerLocalConfig::class.java)
        assertFalse(checkNotNull(obj.enabled))
    }

    @Test
    fun testEmptyObject() {
        val obj = serializer.fromJson("{}", CrashHandlerLocalConfig::class.java)
        assertNull(obj.enabled)
    }
}
