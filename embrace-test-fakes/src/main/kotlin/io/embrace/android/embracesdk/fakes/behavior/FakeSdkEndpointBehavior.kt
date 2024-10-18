package io.embrace.android.embracesdk.fakes.behavior

import io.embrace.android.embracesdk.internal.config.behavior.SdkEndpointBehavior

class FakeSdkEndpointBehavior(
    private val dataEndpoint: String,
    private val configEndpoint: String,
) : SdkEndpointBehavior {
    override fun getData(appId: String?): String = dataEndpoint
    override fun getConfig(appId: String?): String = configEndpoint
}
