package io.embrace.android.embracesdk.config.local

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

internal class StartupMomentLocalConfigTest {

    private val serializer = EmbraceSerializer()

    @Test
    fun testDefaults() {
        val cfg = StartupMomentLocalConfig()
        assertNull(cfg.automaticallyEnd)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("startup_moment_config.json")
        val obj = serializer.fromJson(json, StartupMomentLocalConfig::class.java)
        assertFalse(checkNotNull(obj.automaticallyEnd))
    }

    @Test
    fun testEmptyObject() {
        val obj = serializer.fromJson("{}", StartupMomentLocalConfig::class.java)
        assertNull(obj.automaticallyEnd)
    }
}
