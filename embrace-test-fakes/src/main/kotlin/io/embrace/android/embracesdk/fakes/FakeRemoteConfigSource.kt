package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.config.RemoteConfigSource
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

class FakeRemoteConfigSource(
    var cfg: RemoteConfig? = null
) : RemoteConfigSource {
    override fun getConfig(): RemoteConfig? = cfg
}
