package io.embrace.android.embracesdk.config

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.capture.internal.errors.InternalErrorType
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
import io.embrace.android.embracesdk.config.behavior.OTelBehavior
import io.embrace.android.embracesdk.config.behavior.SdkEndpointBehavior
import io.embrace.android.embracesdk.config.behavior.SdkModeBehavior
import io.embrace.android.embracesdk.config.behavior.SessionBehavior
import io.embrace.android.embracesdk.config.behavior.StartupBehavior
import io.embrace.android.embracesdk.config.behavior.WebViewVitalsBehavior
import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.prefs.PreferencesService
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateListener
import io.embrace.android.embracesdk.utils.stream
import io.embrace.android.embracesdk.worker.BackgroundWorker
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.min

/**
 * Loads configuration for the app from the Embrace API.
 */
internal class EmbraceConfigService @JvmOverloads constructor(
    private val localConfig: LocalConfig,
    private val apiService: ApiService?,
    private val preferencesService: PreferencesService,
    private val clock: Clock,
    private val logger: EmbLogger,
    private val backgroundWorker: BackgroundWorker,
    isDebug: Boolean,
    internal val thresholdCheck: BehaviorThresholdCheck =
        BehaviorThresholdCheck(preferencesService::deviceIdentifier)
) : ConfigService, ProcessStateListener {

    /**
     * The listeners subscribed to configuration changes.
     */
    private val listeners: MutableSet<() -> Unit> = CopyOnWriteArraySet()
    private val lock = Any()

    @Volatile
    private var configProp = RemoteConfig()

    @Volatile
    var lastUpdated: Long = 0

    @Volatile
    private var lastRefreshConfigAttempt: Long = 0

    @Volatile
    private var configRetrySafeWindow = DEFAULT_RETRY_WAIT_TIME.toDouble()

    private val remoteSupplier: Provider<RemoteConfig?> = { getConfig() }

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

    override val oTelBehavior: OTelBehavior =
        OTelBehavior(
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
        backgroundWorker.submit(runnable = ::loadConfigFromCache)
    }

    /**
     * Load Config from cache if present.
     */

    fun loadConfigFromCache() {
        val cachedConfig = apiService?.getCachedConfig()
        val obj = cachedConfig?.remoteConfig

        if (obj != null) {
            val oldConfig = configProp
            updateConfig(oldConfig, obj)
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
                    val newConfig = apiService?.getConfig()
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
                    logger.logWarning(
                        "Failed to load SDK config from the server. " +
                            "Trying again in " + configRetrySafeWindow + " seconds."
                    )
                }
            }
        }
    }

    private fun updateConfig(previousConfig: RemoteConfig, newConfig: RemoteConfig) {
        if (newConfig != previousConfig) {
            configProp = newConfig
            persistConfig()
            // Only notify listeners if the config has actually changed value
            notifyListeners()
        }
    }

    private fun persistConfig() {
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

    override fun addListener(configListener: () -> Unit) {
        listeners.add(configListener)
    }

    override fun onForeground(coldStart: Boolean, timestamp: Long) {
        // Refresh the config on resume if it has expired
        getConfig()
        if (Embrace.getInstance().isStarted && isSdkDisabled()) {
            logger.logInfo("Embrace SDK disabled by config")
            Embrace.getInstance().internalInterface.stopSdk()
        }
    }

    /**
     * Notifies the listeners that a new config was fetched from the server.
     */
    private fun notifyListeners() {
        stream(listeners) { listener ->
            try {
                listener()
            } catch (ex: Exception) {
                logger.logWarning("Failed to notify configListener", ex)
                logger.trackInternalError(InternalErrorType.CONFIG_LISTENER_FAIL, ex)
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
