package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.store.RemoteConfigStore

class FakeRemoteConfigStore(
    val impl: RemoteConfig? = null
) : RemoteConfigStore {
    override fun getConfig(): RemoteConfig? = impl

    override fun save(config: RemoteConfig) {
    }
}
