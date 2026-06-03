package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.config.source.ConfigHttpResponse
import io.embrace.android.embracesdk.internal.config.store.RemoteConfigStore

class FakeRemoteConfigStore(
    var impl: ConfigHttpResponse? = null,
) : RemoteConfigStore {

    var saveCount: Int = 0

    var deviceId: String? = null

    override fun loadResponse(): ConfigHttpResponse? = impl

    override fun saveResponse(response: ConfigHttpResponse) {
        saveCount++
        impl = response
    }

    override fun loadDeviceId(): String? = deviceId
}
