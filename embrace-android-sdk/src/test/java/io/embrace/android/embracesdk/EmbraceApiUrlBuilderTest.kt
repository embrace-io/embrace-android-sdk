package io.embrace.android.embracesdk

import android.content.Context
import io.embrace.android.embracesdk.comms.api.ApiUrlBuilder
import io.embrace.android.embracesdk.comms.api.EmbraceApiUrlBuilder
import io.embrace.android.embracesdk.config.local.BaseUrlLocalConfig
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.fakeSdkEndpointBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.prefs.PreferencesService
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class EmbraceApiUrlBuilderTest {

    private lateinit var preferenceService: PreferencesService
    private lateinit var context: Context
    private lateinit var apiUrlBuilder: ApiUrlBuilder

    @Before
    fun setup() {
        preferenceService = FakePreferenceService(
            deviceIdentifier = DEVICE_ID
        )
        context = FakeCoreModule().context
        apiUrlBuilder = EmbraceApiUrlBuilder(
            enableIntegrationTesting = false,
            isDebug = false,
            sdkEndpointBehavior = fakeSdkEndpointBehavior(localCfg = { BaseUrlLocalConfig() }),
            appId = lazy { APP_ID },
            deviceId = lazy { preferenceService.deviceIdentifier },
            context = context,
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testUrls() {
        assertEquals(
            "https://a-$APP_ID.config.emb-api.com/v2/config?appId=o0o0o&osVersion=0.0.0" +
                "&appVersion=1.0.0&deviceId=${preferenceService.deviceIdentifier}",
            apiUrlBuilder.getConfigUrl()
        )
        assertEquals(
            "https://a-$APP_ID.data.emb-api.com/v1/log/suffix",
            apiUrlBuilder.getEmbraceUrlWithSuffix("suffix")
        )
    }
}

private const val APP_ID = "o0o0o"
private const val DEVICE_ID = "07D85B44E4E245F4A30E559BFC0D07FF"
