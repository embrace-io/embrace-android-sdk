package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.store.RemoteConfigStore

class FakeRemoteConfigStore(
    val impl: RemoteConfig? = null,
    var etag: String? = null,
) : RemoteConfigStore {

    override fun loadConfig(): RemoteConfig? = impl

    override fun saveConfig(config: RemoteConfig) {
    }

    override fun retrieveEtag(): String? = etag

    override fun storeEtag(etag: String) {
        this.etag = etag
    }
}
