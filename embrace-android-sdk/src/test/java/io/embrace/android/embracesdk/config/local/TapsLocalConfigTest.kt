package io.embrace.android.embracesdk.config.local

import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import io.embrace.android.embracesdk.internal.config.local.TapsLocalConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

internal class TapsLocalConfigTest {

    @Test
    fun testDefaults() {
        val cfg = TapsLocalConfig()
        assertNull(cfg.captureCoordinates)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<TapsLocalConfig>("taps_config.json")
        assertFalse(checkNotNull(obj.captureCoordinates))
    }

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<TapsLocalConfig>()
        assertNull(obj.captureCoordinates)
    }
}
