package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.comms.api.ApiUrlBuilder
import io.embrace.android.embracesdk.internal.comms.api.Endpoint

class FakeApiUrlBuilder(
    override val appId: String = "",
    override val deviceId: String = "",
    override val baseDataUrl: String = "",
) : ApiUrlBuilder {
    override fun resolveUrl(endpoint: Endpoint): String = ""
}
