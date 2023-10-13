package io.embrace.android.embracesdk.config.local

import com.google.gson.Gson
import io.embrace.android.embracesdk.ResourceReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class DomainLocalConfigTest {

    @Test
    fun testDefaults() {
        val cfg = DomainLocalConfig()
        verifyDefaults(cfg)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("domain_config.json")
        val obj = Gson().fromJson(json, DomainLocalConfig::class.java)
        assertEquals("example-apis.com", obj.domain)
        assertEquals(400, obj.limit)
    }

    @Test
    fun testEmptyObject() {
        val obj = Gson().fromJson("{}", DomainLocalConfig::class.java)
        verifyDefaults(obj)
    }

    private fun verifyDefaults(cfg: DomainLocalConfig) {
        assertNull(cfg.domain)
        assertNull(cfg.limit)
    }
}
