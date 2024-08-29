package io.embrace.android.embracesdk.internal.comms.api

import io.embrace.android.embracesdk.fakes.fakeSdkEndpointBehavior
import io.embrace.android.embracesdk.internal.config.local.BaseUrlLocalConfig
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private const val APP_ID = "o0o0o"
private const val APP_VERSION_NAME = "1.0.0"
private const val DEVICE_ID = "07D85B44E4E245F4A30E559BFC0D07FF"

internal class EmbraceApiUrlBuilderTest {
    private lateinit var apiUrlBuilder: ApiUrlBuilder

    @Before
    fun setup() {
        val baseUrlLocalConfig = fakeSdkEndpointBehavior { BaseUrlLocalConfig() }

        apiUrlBuilder = EmbraceApiUrlBuilder(
            coreBaseUrl = baseUrlLocalConfig.getData(APP_ID),
            configBaseUrl = baseUrlLocalConfig.getConfig(APP_ID),
            appId = APP_ID,
            lazyDeviceId = lazy { DEVICE_ID },
            lazyAppVersionName = lazy { APP_VERSION_NAME },
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testUrls() {
        assertEquals(
            "https://a-$APP_ID.config.emb-api.com/v2/config?appId=$APP_ID&osVersion=0.0.0" +
                "&appVersion=$APP_VERSION_NAME&deviceId=$DEVICE_ID",
            apiUrlBuilder.getConfigUrl()
        )
        assertEquals(
            "https://a-$APP_ID.data.emb-api.com/v1/log/suffix",
            apiUrlBuilder.getEmbraceUrlWithSuffix("v1", "suffix")
        )
        assertEquals(
            "https://a-$APP_ID.data.emb-api.com/v2/suffix",
            apiUrlBuilder.getEmbraceUrlWithSuffix("v2", "suffix")
        )
    }
}
