package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.comms.api.ApiUrlBuilder

class FakeApiUrlBuilder(
    private val config: String = "",
    private val other: String = "",
    override val appId: String = "",
    override val deviceId: String = "",
) : ApiUrlBuilder {

    override fun getConfigUrl(): String = config

    override fun getEmbraceUrlWithSuffix(apiVersion: String, suffix: String): String = other
}
