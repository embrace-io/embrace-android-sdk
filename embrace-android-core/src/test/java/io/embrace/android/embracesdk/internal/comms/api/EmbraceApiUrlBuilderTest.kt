package io.embrace.android.embracesdk.internal.comms.api

import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private const val APP_ID = "abcde"
private const val APP_VERSION_NAME = "1.0.0"
private const val DEVICE_ID = "07D85B44E4E245F4A30E559BFC0D07FF"

internal class EmbraceApiUrlBuilderTest {
    private lateinit var apiUrlBuilder: ApiUrlBuilder

    @Before
    fun setup() {
        apiUrlBuilder = EmbraceApiUrlBuilder(
            deviceId = DEVICE_ID,
            appVersionName = APP_VERSION_NAME,
            FakeInstrumentedConfig()
        )
    }

    @Test
    fun testUrls() {
        assertEquals(
            "https://a-$APP_ID.config.emb-api.com/v2/config?appId=$APP_ID&osVersion=0.0.0" +
                "&appVersion=$APP_VERSION_NAME&deviceId=$DEVICE_ID",
            apiUrlBuilder.resolveUrl(Endpoint.CONFIG)
        )
        assertEquals(
            "https://a-$APP_ID.data.emb-api.com/api/v2/logs",
            apiUrlBuilder.resolveUrl(Endpoint.LOGS)
        )
        assertEquals(
            "https://a-$APP_ID.data.emb-api.com/api/v2/spans",
            apiUrlBuilder.resolveUrl(Endpoint.SESSIONS)
        )
    }
}
