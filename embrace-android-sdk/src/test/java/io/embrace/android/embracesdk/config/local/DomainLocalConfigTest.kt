package io.embrace.android.embracesdk.config.local

import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import io.embrace.android.embracesdk.internal.config.local.DomainLocalConfig
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
        val obj = deserializeJsonFromResource<DomainLocalConfig>("domain_config.json")
        assertEquals("example-apis.com", obj.domain)
        assertEquals(400, obj.limit)
    }

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<DomainLocalConfig>()
        verifyDefaults(obj)
    }

    private fun verifyDefaults(cfg: DomainLocalConfig) {
        assertNull(cfg.domain)
        assertNull(cfg.limit)
    }
}
