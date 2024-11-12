package io.embrace.android.embracesdk.internal.config.source

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateListener
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import kotlin.math.min

/**
 * Loads configuration for the app from the Embrace API.
 */
class RemoteConfigSourceImpl(
    private val clock: Clock,
    private val backgroundWorker: BackgroundWorker,
    private val foregroundAction: () -> Unit,
) : RemoteConfigSource, ProcessStateListener {

    private val lock = Any()

    var remoteConfigSource: CachedRemoteConfigSource? = null
        set(value) {
            field = value
            loadConfigFromCache()
            attemptConfigRefresh()
        }

    override fun setInitialEtag(etag: String) {
        // no-op
    }

    @Volatile
    private var configProp = RemoteConfig()

    @Volatile
    var lastUpdated: Long = 0

    @Volatile
    private var lastRefreshConfigAttempt: Long = 0

    @Volatile
    private var configRetrySafeWindow = DEFAULT_RETRY_WAIT_TIME.toDouble()

    override fun getConfig(): RemoteConfig {
        attemptConfigRefresh()
        return configProp
    }

    /**
     * Load Config from cache if present.
     */
    internal fun loadConfigFromCache() {
        val cachedConfig = remoteConfigSource?.getCachedConfig()
        val obj = cachedConfig?.remoteConfig

        if (obj != null) {
            val oldConfig = configProp
            updateConfig(oldConfig, obj)
        }
    }

    private fun attemptConfigRefresh() {
        if (configRequiresRefresh() && configRetryIsSafe()) {
            synchronized(lock) {
                if (configRequiresRefresh() && configRetryIsSafe()) {
                    lastRefreshConfigAttempt = clock.now()
                    // Attempt to asynchronously update the config if it is out of date
                    refreshConfig()
                }
            }
        }
    }

    private fun refreshConfig() {
        val previousConfig = configProp
        backgroundWorker.submit {
            // Ensure that another thread didn't refresh it already in the meantime
            if (configRequiresRefresh()) {
                try {
                    lastRefreshConfigAttempt = clock.now()
                    val newConfig = remoteConfigSource?.getConfig()
                    if (newConfig != null) {
                        updateConfig(previousConfig, newConfig)
                        lastUpdated = clock.now()
                    }
                    configRetrySafeWindow = DEFAULT_RETRY_WAIT_TIME.toDouble()
                } catch (ex: Exception) {
                    configRetrySafeWindow =
                        min(
                            MAX_ALLOWED_RETRY_WAIT_TIME.toDouble(),
                            configRetrySafeWindow * 2
                        )
                }
            }
        }
    }

    private fun updateConfig(previousConfig: RemoteConfig, newConfig: RemoteConfig) {
        val b = newConfig != previousConfig
        if (b) {
            configProp = newConfig
        }
    }

    override fun onForeground(coldStart: Boolean, timestamp: Long) {
        // Refresh the config on resume if it has expired
        getConfig()
        foregroundAction()
    }

    /**
     * Checks if the time diff since the last fetch exceeds the
     * [RemoteConfigSourceImpl.CONFIG_TTL] millis.
     *
     * @return if the config requires to be fetched from the remote server again or not.
     */
    private fun configRequiresRefresh(): Boolean {
        return clock.now() - lastUpdated > CONFIG_TTL
    }

    /**
     * Checks if the time diff since the last attempt is enough to try again.
     *
     * @return if the config can be fetched from the remote server again or not.
     */
    private fun configRetryIsSafe(): Boolean {
        return clock.now() > lastRefreshConfigAttempt + configRetrySafeWindow * 1000
    }

    private companion object {

        /**
         * Config lives for 1 hour before attempting to retrieve again.
         */
        private const val CONFIG_TTL = 60 * 60 * 1000L

        /**
         * Config refresh default retry period.
         */
        private const val DEFAULT_RETRY_WAIT_TIME: Long = 20 // 20 seconds

        /**
         * Config max allowed refresh retry period.
         */
        private const val MAX_ALLOWED_RETRY_WAIT_TIME: Long = 300 // 5 minutes
    }
}
