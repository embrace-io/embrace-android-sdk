package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.comms.api.ApiUrlBuilder
import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.fakes.FakeAndroidMetadataService
import io.embrace.android.embracesdk.fakes.FakeCoreConfigService
import io.embrace.android.embracesdk.fakes.fakeSdkAppBehavior
import org.junit.Assert.assertEquals
import org.junit.Test

internal class ApiUrlBuilderTest {

    private val coreConfigService = FakeCoreConfigService(
        sdkAppBehavior = fakeSdkAppBehavior(
            localCfg = { LocalConfig("o0o0o", false, SdkLocalConfig()) },
        )
    )
    private val metadataService = FakeAndroidMetadataService()

    @Test
    fun testUrls() {
        val builder = ApiUrlBuilder(
            coreConfigService = coreConfigService,
            metadataService = metadataService,
            enableIntegrationTesting = false,
            isDebug = false
        )
        assertEquals(
            "https://a-o0o0o.config.emb-api.com/v2/config?appId=o0o0o&osVersion=0.0.0" +
                "&appVersion=1.0.0&deviceId=07D85B44E4E245F4A30E559BFC0D07FF",
            builder.getConfigUrl()
        )
        assertEquals(
            "https://a-o0o0o.data.emb-api.com/v1/log/suffix",
            builder.getEmbraceUrlWithSuffix("suffix")
        )
    }
}
