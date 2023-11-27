package io.embrace.android.embracesdk.config.local

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class BaseUrlLocalConfigTest {

    private val serializer = EmbraceSerializer()

    @Test
    fun testDefaults() {
        val cfg = BaseUrlLocalConfig()
        verifyDefaults(cfg)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("base_url_config.json")
        val obj = serializer.fromJson(json, BaseUrlLocalConfig::class.java)
        assertEquals("https://config.example.com", obj.config)
        assertEquals("https://data.example.com", obj.data)
        assertEquals("https://data-dev.example.com", obj.dataDev)
        assertEquals("https://images.example.com", obj.images)
    }

    @Test
    fun testEmptyObject() {
        val obj = serializer.fromJson("{}", BaseUrlLocalConfig::class.java)
        verifyDefaults(obj)
    }

    private fun verifyDefaults(obj: BaseUrlLocalConfig) {
        assertNull("https://config.emb-api.com", obj.config)
        assertNull("https://data.emb-api.com", obj.data)
        assertNull("https://data-dev.emb-api.com", obj.dataDev)
        assertNull("https://images.emb-api.com", obj.images)
    }
}
