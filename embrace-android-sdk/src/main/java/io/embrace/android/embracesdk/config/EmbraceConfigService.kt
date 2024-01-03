package io.embrace.android.embracesdk.config

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.comms.api.ApiService
import io.embrace.android.embracesdk.config.behavior.AnrBehavior
import io.embrace.android.embracesdk.config.behavior.AppExitInfoBehavior
import io.embrace.android.embracesdk.config.behavior.AutoDataCaptureBehavior
import io.embrace.android.embracesdk.config.behavior.BackgroundActivityBehavior
import io.embrace.android.embracesdk.config.behavior.BehaviorThresholdCheck
import io.embrace.android.embracesdk.config.behavior.BreadcrumbBehavior
import io.embrace.android.embracesdk.config.behavior.DataCaptureEventBehavior
import io.embrace.android.embracesdk.config.behavior.LogMessageBehavior
import io.embrace.android.embracesdk.config.behavior.NetworkBehavior
import io.embrace.android.embracesdk.config.behavior.NetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.config.behavior.SdkEndpointBehavior
import io.embrace.android.embracesdk.config.behavior.SdkModeBehavior
import io.embrace.android.embracesdk.config.behavior.SessionBehavior
import io.embrace.android.embracesdk.config.behavior.StartupBehavior
import io.embrace.android.embracesdk.config.behavior.WebViewVitalsBehavior
import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.prefs.PreferencesService
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateListener
import io.embrace.android.embracesdk.utils.stream
import java.util.concurrent.Callable
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ExecutorService
import kotlin.math.min

/**
 * Loads configuration for the app from the Embrace API.
 */
internal class EmbraceConfigService @JvmOverloads constructor(
    private val localConfig: LocalConfig,
    private val apiService: ApiService,
    private val preferencesService: PreferencesService,
    private val clock: Clock,
    private val logger: InternalEmbraceLogger,
    private val executorService: ExecutorService,
    isDebug: Boolean,
    private val stopBehavior: () -> Unit = {},
    internal val thresholdCheck: BehaviorThresholdCheck = BehaviorThresholdCheck(preferencesService::deviceIdentifier)
) : ConfigService, ProcessStateListener {

    /**
     * The listeners subscribed to configuration changes.
     */
    private val listeners: MutableSet<ConfigListener> = CopyOnWriteArraySet()
    private val lock = Any()

    @Volatile
    private var configProp = RemoteConfig()

    @Volatile
    var lastUpdated: Long = 0

    @Volatile
    private var lastRefreshConfigAttempt: Long = 0

    @Volatile
    private var configRetrySafeWindow = DEFAULT_RETRY_WAIT_TIME.toDouble()

    private val remoteSupplier: () -> RemoteConfig? = { getConfig() }

    override val backgroundActivityBehavior: BackgroundActivityBehavior =
        BackgroundActivityBehavior(
            thresholdCheck = thresholdCheck,
            localSupplier = localConfig.sdkConfig::backgroundActivityConfig,
            remoteSupplier = { getConfig().backgroundActivityConfig }
        )

    override val autoDataCaptureBehavior: AutoDataCaptureBehavior =
        AutoDataCaptureBehavior(
            thresholdCheck = thresholdCheck,
            localSupplier = { localConfig },
            remoteSupplier = remoteSupplier
        )

    override val breadcrumbBehavior: BreadcrumbBehavior =
        BreadcrumbBehavior(
            thresholdCheck,
            localSupplier = localConfig::sdkConfig,
            remoteSupplier = remoteSupplier
        )

    override val logMessageBehavior: LogMessageBehavior =
        LogMessageBehavior(
            thresholdCheck,
            remoteSupplier = { getConfig().logConfig }
        )

    override val anrBehavior: AnrBehavior =
        AnrBehavior(
            thresholdCheck,
            localSupplier = localConfig.sdkConfig::anr,
            remoteSupplier = { getConfig().anrConfig }
        )

    override val sessionBehavior: SessionBehavior =
        SessionBehavior(
            thresholdCheck,
            localSupplier = localConfig.sdkConfig::sessionConfig,
            remoteSupplier = { getConfig() }
        )

    override val networkBehavior: NetworkBehavior =
        NetworkBehavior(
            thresholdCheck = thresholdCheck,
            localSupplier = localConfig::sdkConfig,
            remoteSupplier = remoteSupplier
        )

    override val startupBehavior: StartupBehavior =
        StartupBehavior(
            thresholdCheck = thresholdCheck,
            localSupplier = localConfig.sdkConfig::startupMoment
        )

    override val dataCaptureEventBehavior: DataCaptureEventBehavior = DataCaptureEventBehavior(
        thresholdCheck = thresholdCheck,
        remoteSupplier = remoteSupplier
    )

    override val sdkModeBehavior: SdkModeBehavior =
        SdkModeBehavior(
            isDebug = isDebug,
            thresholdCheck = thresholdCheck,
            localSupplier = { localConfig },
            remoteSupplier = remoteSupplier
        )

    override val sdkEndpointBehavior: SdkEndpointBehavior =
        SdkEndpointBehavior(
            thresholdCheck = thresholdCheck,
            localSupplier = localConfig.sdkConfig::baseUrls,
        )

    override val appExitInfoBehavior: AppExitInfoBehavior = AppExitInfoBehavior(
        thresholdCheck = thresholdCheck,
        localSupplier = localConfig.sdkConfig::appExitInfoConfig,
        remoteSupplier = remoteSupplier
    )

    override val networkSpanForwardingBehavior: NetworkSpanForwardingBehavior =
        NetworkSpanForwardingBehavior(
            thresholdCheck = thresholdCheck,
            remoteSupplier = { getConfig().networkSpanForwardingRemoteConfig }
        )

    override val webViewVitalsBehavior: WebViewVitalsBehavior =
        WebViewVitalsBehavior(
            thresholdCheck = thresholdCheck,
            remoteSupplier = remoteSupplier
        )

    init {
        performInitialConfigLoad()
        attemptConfigRefresh()
    }

    /**
     * Schedule an action that loads the config from the cache.
     * This is deferred to lessen it´s impact upon startup.
     */
    private fun performInitialConfigLoad() {
        logger.logDeveloper("EmbraceConfigService", "performInitialConfigLoad")
        executorService.submit(::loadConfigFromCache)
    }

    /**
     * Load Config from cache if present.
     */

    fun loadConfigFromCache() {
        logger.logDeveloper("EmbraceConfigService", "Attempting to load config from cache")
        val cachedConfig = apiService.getCachedConfig()
        val obj = cachedConfig.remoteConfig

        if (obj != null) {
            val oldConfig = configProp
            logger.logDeveloper("EmbraceConfigService", "Loaded config from cache")
            updateConfig(oldConfig, obj)
        } else {
            logger.logDeveloper("EmbraceConfigService", "config not found in local cache")
        }
    }

    private fun getConfig(): RemoteConfig {
        attemptConfigRefresh()
        return configProp
    }

    private fun attemptConfigRefresh() {
        if (configRequiresRefresh() && configRetryIsSafe()) {
            synchronized(lock) {
                if (configRequiresRefresh() && configRetryIsSafe()) {
                    lastRefreshConfigAttempt = clock.now()
                    logger.logDeveloper("EmbraceConfigService", "Attempting to update config")
                    // Attempt to asynchronously update the config if it is out of date
                    refreshConfig()
                }
            }
        }
    }

    private fun refreshConfig() {
        logger.logDeveloper("EmbraceConfigService", "Attempting to refresh config")
        val previousConfig = configProp
        executorService.submit(
            Callable<Any> {
                logger.logDeveloper("EmbraceConfigService", "Updating config in background thread")

                // Ensure that another thread didn't refresh it already in the meantime
                if (configRequiresRefresh()) {
                    try {
                        lastRefreshConfigAttempt = clock.now()
                        val newConfig = apiService.getConfig()
                        if (newConfig != null) {
                            updateConfig(previousConfig, newConfig)
                            lastUpdated = clock.now()
                        }
                        configRetrySafeWindow = DEFAULT_RETRY_WAIT_TIME.toDouble()
                        logger.logDeveloper("EmbraceConfigService", "Config updated")
                    } catch (ex: Exception) {
                        configRetrySafeWindow =
                            min(
                                MAX_ALLOWED_RETRY_WAIT_TIME.toDouble(),
                                configRetrySafeWindow * 2
                            )
                        logger.logWarning(
                            "Failed to load SDK config from the server. " +
                                "Trying again in " + configRetrySafeWindow + " seconds."
                        )
                    }
                }
                configProp
            }
        )
    }

    private fun updateConfig(previousConfig: RemoteConfig, newConfig: RemoteConfig) {
        if (newConfig != previousConfig) {
            configProp = newConfig
            persistConfig()
            logger.logDeveloper("EmbraceConfigService", "Notify listeners about new config")
            // Only notify listeners if the config has actually changed value
            notifyListeners()
        }
    }

    private fun persistConfig() {
        logger.logDeveloper("EmbraceConfigService", "persistConfig")
        // TODO: future get rid of these prefs from PrefService entirely?
        preferencesService.sdkDisabled = sdkModeBehavior.isSdkDisabled()
        preferencesService.backgroundActivityEnabled = backgroundActivityBehavior.isEnabled()
    }

    // TODO: future extract these out to SdkBehavior interface
    override fun isSdkDisabled(): Boolean {
        return preferencesService.sdkDisabled
    }

    override fun isBackgroundActivityCaptureEnabled(): Boolean {
        return preferencesService.backgroundActivityEnabled
    }

    override fun addListener(configListener: ConfigListener) {
        listeners.add(configListener)
    }

    override fun onForeground(coldStart: Boolean, startupTime: Long, timestamp: Long) {
        // Refresh the config on resume if it has expired
        getConfig()
        if (Embrace.getInstance().isStarted && isSdkDisabled()) {
            logger.logInfo("Embrace SDK disabled by config")
            stopBehavior()
        }
    }

    /**
     * Notifies the listeners that a new config was fetched from the server.
     */
    private fun notifyListeners() {
        stream(listeners) { listener: ConfigListener ->
            try {
                listener.onConfigChange(this)
            } catch (ex: Exception) {
                logger.logDebug("Failed to notify ConfigListener", ex)
            }
        }
    }

    /**
     * Checks if the time diff since the last fetch exceeds the
     * [EmbraceConfigService.CONFIG_TTL] millis.
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

    override fun close() {
        logger.logDebug("Shutting down EmbraceConfigService")
    }

    override fun hasValidRemoteConfig(): Boolean = !configRequiresRefresh()
    override fun isAppExitInfoCaptureEnabled(): Boolean {
        return appExitInfoBehavior.isEnabled()
    }

    companion object {

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
