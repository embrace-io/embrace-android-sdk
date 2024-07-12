package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.config.local.BaseUrlLocalConfig
import io.embrace.android.embracesdk.fakes.fakeSdkEndpointBehavior
import org.junit.Assert.assertEquals
import org.junit.Test

internal class SdkEndpointBehaviorImplTest {

    private val local = BaseUrlLocalConfig(
        "https://config.example.com",
        "https://data.example.com",
        "https://images.example.com"
    )

    @Test
    fun testDefaults() {
        with(fakeSdkEndpointBehavior()) {
            assertEquals("https://a-12345.config.emb-api.com", getConfig("12345"))
            assertEquals("https://a-12345.data.emb-api.com", getData("12345"))
        }
    }

    @Test
    fun testLocalOnly() {
        with(fakeSdkEndpointBehavior(localCfg = { local })) {
            assertEquals("https://config.example.com", getConfig("12345"))
            assertEquals("https://data.example.com", getData("12345"))
        }
    }
}
