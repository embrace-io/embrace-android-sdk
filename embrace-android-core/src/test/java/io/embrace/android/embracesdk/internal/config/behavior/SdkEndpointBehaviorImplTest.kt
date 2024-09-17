package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.createSdkEndpointBehavior
import org.junit.Assert.assertEquals
import org.junit.Test

internal class SdkEndpointBehaviorImplTest {

    @Test
    fun testDefaults() {
        with(createSdkEndpointBehavior()) {
            assertEquals("https://a-12345.config.emb-api.com", getConfig("12345"))
            assertEquals("https://a-12345.data.emb-api.com", getData("12345"))
        }
    }
}
