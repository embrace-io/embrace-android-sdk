package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.comms.api.ApiUrlBuilder

internal class FakeApiUrlBuilder : ApiUrlBuilder {
    override fun getConfigUrl(): String {
        return ""
    }

    override fun getEmbraceUrlWithSuffix(suffix: String): String {
        return ""
    }
}
