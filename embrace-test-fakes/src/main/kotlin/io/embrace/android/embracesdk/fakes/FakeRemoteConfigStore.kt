package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.store.RemoteConfigStore

class FakeRemoteConfigStore : RemoteConfigStore {
    override fun getConfig(): RemoteConfig? = null

    override fun save(config: RemoteConfig) {
    }
}
