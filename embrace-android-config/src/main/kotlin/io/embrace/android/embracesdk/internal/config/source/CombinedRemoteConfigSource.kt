package io.embrace.android.embracesdk.internal.config.source

import io.embrace.android.embracesdk.internal.config.store.RemoteConfigStore
import io.embrace.android.embracesdk.internal.config.store.StoredConfigResponse
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.TimeUnit

internal class CombinedRemoteConfigSource(
    private val store: RemoteConfigStore,
    private val response: StoredConfigResponse?,
    httpSource: Lazy<RemoteConfigSource>,
    private val worker: BackgroundWorker,
    private val intervalMs: Long = 60 * 60 * 1000,
) {

    private val httpSource: RemoteConfigSource by httpSource

    fun scheduleConfigRequests() {
        worker.scheduleWithFixedDelay(
            ::attemptConfigRequest,
            0,
            intervalMs,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun attemptConfigRequest() {
        response?.etag?.let {
            httpSource.setInitialEtag(it)
        }
        httpSource.getConfig()?.let {
            store.saveResponse(it)
        }
    }
}
