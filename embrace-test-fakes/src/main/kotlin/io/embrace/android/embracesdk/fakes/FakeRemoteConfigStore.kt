package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.config.source.ConfigHttpResponse
import io.embrace.android.embracesdk.internal.config.store.RemoteConfigStore

class FakeRemoteConfigStore(
    var impl: ConfigHttpResponse? = null,
) : RemoteConfigStore {

    override fun loadResponse(): ConfigHttpResponse? = impl

    override fun saveResponse(response: ConfigHttpResponse) {
        impl = response
    }
}
