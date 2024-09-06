package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.comms.api.ApiUrlBuilder

class FakeApiUrlBuilder : ApiUrlBuilder {
    override fun getConfigUrl(): String {
        return ""
    }

    override fun getEmbraceUrlWithSuffix(apiVersion: String, suffix: String): String {
        return ""
    }
}
