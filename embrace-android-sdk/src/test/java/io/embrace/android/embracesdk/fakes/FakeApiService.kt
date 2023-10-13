package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.comms.api.ApiService
import io.embrace.android.embracesdk.comms.api.CachedConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig

internal class FakeApiService : ApiService {
    override fun getConfig(): RemoteConfig? {
        TODO("Not yet implemented")
    }

    override fun getCachedConfig(): CachedConfig {
        TODO("Not yet implemented")
    }
}
