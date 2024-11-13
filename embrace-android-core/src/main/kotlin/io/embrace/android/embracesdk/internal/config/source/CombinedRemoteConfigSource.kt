package io.embrace.android.embracesdk.internal.config.source

import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.store.RemoteConfigStore
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.TimeUnit

class CombinedRemoteConfigSource(
    private val store: RemoteConfigStore,
    private val httpSource: RemoteConfigSource,
    private val worker: BackgroundWorker,
    private val intervalMs: Long = 60 * 60 * 1000
) {

    // the remote config that is used for the lifetime of the process.
    private val response by lazy { store.loadResponse() }

    fun getConfig(): RemoteConfig? = response?.cfg

    fun scheduleConfigRequests() {
        response?.etag?.let(httpSource::setInitialEtag)
        worker.scheduleWithFixedDelay(
            ::attemptConfigRequest,
            0,
            intervalMs,
            TimeUnit.MILLISECONDS
        )
    }

    private fun attemptConfigRequest() {
        httpSource.getConfig()?.let(store::saveResponse)
    }
}
