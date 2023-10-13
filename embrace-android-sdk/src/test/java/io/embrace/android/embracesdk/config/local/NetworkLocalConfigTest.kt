package io.embrace.android.embracesdk.config.local

import com.google.gson.Gson
import io.embrace.android.embracesdk.ResourceReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class NetworkLocalConfigTest {

    @Test
    fun testDefaults() {
        val cfg = NetworkLocalConfig()
        verifyDefaults(cfg)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("local_network_config.json")
        val obj = Gson().fromJson(json, NetworkLocalConfig::class.java)

        assertEquals(200, obj.defaultCaptureLimit)
        assertEquals(DomainLocalConfig("google.com", 80).domain, obj.domains?.single()?.domain)
        assertEquals("x-my-header-id", obj.traceIdHeader)
        assertEquals(1, obj.disabledUrlPatterns?.size)
        assertTrue(checkNotNull(obj.captureRequestContentLength))
        assertFalse(checkNotNull(obj.enableNativeMonitoring))
    }

    @Test
    fun testEmptyObject() {
        val obj = Gson().fromJson("{}", NetworkLocalConfig::class.java)
        verifyDefaults(obj)
    }

    private fun verifyDefaults(obj: NetworkLocalConfig) {
        assertNull(obj.defaultCaptureLimit)
        assertNull(obj.domains)
        assertNull(obj.traceIdHeader)
        assertNull(obj.disabledUrlPatterns)
        assertNull(obj.captureRequestContentLength)
        assertNull(obj.enableNativeMonitoring)
    }
}
