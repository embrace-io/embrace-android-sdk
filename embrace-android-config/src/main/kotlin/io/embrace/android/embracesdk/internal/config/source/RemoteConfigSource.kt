package io.embrace.android.embracesdk.internal.config.source

interface RemoteConfigSource {

    /**
     * Gets the remotely delivered configuration that should apply to the app for the lifetime of this process, if any.
     */
    fun getConfig(): ConfigHttpResponse?

    /**
     * Sets the initial ETag to use for the first request, if any.
     */
    fun setInitialEtag(etag: String)
}
