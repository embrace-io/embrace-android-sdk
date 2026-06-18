package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.config.source.ConfigHttpResponse
import io.embrace.android.embracesdk.internal.config.store.RemoteConfigStore
import io.embrace.android.embracesdk.internal.config.store.StoredConfigResponse

internal class FakeRemoteConfigStore(
    var impl: StoredConfigResponse? = null,
) : RemoteConfigStore {

    var saveCount: Int = 0

    override fun loadResponse(): StoredConfigResponse? = impl

    override fun saveResponse(response: ConfigHttpResponse) {
        saveCount++
        impl = StoredConfigResponse(response.cfg, response.etag, deviceId = null)
    }
}
