package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.source.RemoteConfigSource

class FakeRemoteConfigSource(
    var cfg: RemoteConfig? = null
) : RemoteConfigSource {

    var callCount: Int = 0

    override fun getConfig(): RemoteConfig? {
        callCount++
        return cfg
    }
}
