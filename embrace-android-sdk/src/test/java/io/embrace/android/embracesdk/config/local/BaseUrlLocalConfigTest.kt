package io.embrace.android.embracesdk.config.local

import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class BaseUrlLocalConfigTest {

    @Test
    fun testDefaults() {
        val cfg = BaseUrlLocalConfig()
        verifyDefaults(cfg)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<BaseUrlLocalConfig>("base_url_config.json")
        assertEquals("https://config.example.com", obj.config)
        assertEquals("https://data.example.com", obj.data)
        assertEquals("https://images.example.com", obj.images)
    }

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<BaseUrlLocalConfig>()
        verifyDefaults(obj)
    }

    private fun verifyDefaults(obj: BaseUrlLocalConfig) {
        assertNull("https://config.emb-api.com", obj.config)
        assertNull("https://data.emb-api.com", obj.data)
        assertNull("https://images.emb-api.com", obj.images)
    }
}
