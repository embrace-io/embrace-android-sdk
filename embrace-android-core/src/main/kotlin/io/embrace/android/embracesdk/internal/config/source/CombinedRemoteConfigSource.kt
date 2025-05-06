package io.embrace.android.embracesdk.internal.config.source

import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.store.RemoteConfigStore
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.TimeUnit

class CombinedRemoteConfigSource(
    private val store: RemoteConfigStore,
    httpSource: Lazy<RemoteConfigSource>,
    private val worker: BackgroundWorker,
    private val intervalMs: Long = 60 * 60 * 1000
) {

    private val httpSource: RemoteConfigSource by httpSource

    // the remote config that is used for the lifetime of the process.
    private val response by lazy {
        EmbTrace.trace("load-config-from-store") {
            store.loadResponse()
        }
    }

    fun getConfig(): RemoteConfig? = response?.cfg

    fun scheduleConfigRequests() {
        EmbTrace.trace("schedule-http-request") {
            worker.scheduleWithFixedDelay(
                ::attemptConfigRequest,
                0,
                intervalMs,
                TimeUnit.MILLISECONDS
            )
        }
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
