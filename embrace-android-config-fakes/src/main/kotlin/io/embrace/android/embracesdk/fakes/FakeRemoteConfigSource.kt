package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.config.source.ConfigHttpResponse
import io.embrace.android.embracesdk.internal.config.source.RemoteConfigSource

class FakeRemoteConfigSource(
    var cfg: ConfigHttpResponse? = null,

    /**
     * Invoked on every [getConfig] call after [callCount] is incremented. Lets a test observe calls
     * as they happen, or make a chosen call fail.
     */
    var onCall: () -> Unit = {},
) : RemoteConfigSource {

    var callCount: Int = 0
    var etag: String? = null

    override fun getConfig(): ConfigHttpResponse? {
        callCount++
        onCall()
        return cfg
    }

    override fun setInitialEtag(etag: String) {
        this.etag = etag
    }
}
