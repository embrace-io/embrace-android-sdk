package io.embrace.android.embracesdk.internal.config

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.behavior.AnrBehavior
import io.embrace.android.embracesdk.internal.config.behavior.AnrBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.AppExitInfoBehavior
import io.embrace.android.embracesdk.internal.config.behavior.AppExitInfoBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.AutoDataCaptureBehavior
import io.embrace.android.embracesdk.internal.config.behavior.AutoDataCaptureBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.BackgroundActivityBehavior
import io.embrace.android.embracesdk.internal.config.behavior.BackgroundActivityBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.BehaviorThresholdCheck
import io.embrace.android.embracesdk.internal.config.behavior.BreadcrumbBehavior
import io.embrace.android.embracesdk.internal.config.behavior.BreadcrumbBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.DataCaptureEventBehavior
import io.embrace.android.embracesdk.internal.config.behavior.DataCaptureEventBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.LogMessageBehavior
import io.embrace.android.embracesdk.internal.config.behavior.LogMessageBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.NetworkBehavior
import io.embrace.android.embracesdk.internal.config.behavior.NetworkBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.NetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.internal.config.behavior.NetworkSpanForwardingBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.SdkEndpointBehavior
import io.embrace.android.embracesdk.internal.config.behavior.SdkEndpointBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.SdkModeBehavior
import io.embrace.android.embracesdk.internal.config.behavior.SdkModeBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehavior
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.SessionBehavior
import io.embrace.android.embracesdk.internal.config.behavior.SessionBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.StartupBehavior
import io.embrace.android.embracesdk.internal.config.behavior.StartupBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.WebViewVitalsBehavior
import io.embrace.android.embracesdk.internal.config.behavior.WebViewVitalsBehaviorImpl
import io.embrace.android.embracesdk.internal.config.local.LocalConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateListener
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.stream
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.min

/**
 * Loads configuration for the app from the Embrace API.
 */
internal class EmbraceConfigService(
    private val localConfig: LocalConfig,
    private val preferencesService: PreferencesService,
    private val clock: Clock,
    private val logger: EmbLogger,
    private val backgroundWorker: BackgroundWorker,
    suppliedFramework: AppFramework,
    private val foregroundAction: ConfigService.() -> Unit,
    val thresholdCheck: BehaviorThresholdCheck =
        BehaviorThresholdCheck { preferencesService.deviceIdentifier }
) : ConfigService, ProcessStateListener {

    /**
     * The listeners subscribed to configuration changes.
     */
    private val listeners: MutableSet<() -> Unit> = CopyOnWriteArraySet()
    private val lock = Any()
    override var remoteConfigSource: RemoteConfigSource? = null
        set(value) {
            field = value
            performInitialConfigLoad()
            attemptConfigRefresh()
        }

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
        BackgroundActivityBehaviorImpl(
            thresholdCheck = thresholdCheck,
            localSupplier = localConfig.sdkConfig::backgroundActivityConfig,
            remoteSupplier = { getConfig().backgroundActivityConfig }
        )

    override val autoDataCaptureBehavior: AutoDataCaptureBehavior =
        AutoDataCaptureBehaviorImpl(
            thresholdCheck = thresholdCheck,
            localSupplier = { localConfig },
            remoteSupplier = remoteSupplier
        )

    override val breadcrumbBehavior: BreadcrumbBehavior =
        BreadcrumbBehaviorImpl(
            thresholdCheck,
            localSupplier = localConfig::sdkConfig,
            remoteSupplier = remoteSupplier
        )

    override val sensitiveKeysBehavior: SensitiveKeysBehavior = SensitiveKeysBehaviorImpl(localConfig.sdkConfig)

    override val logMessageBehavior: LogMessageBehavior =
        LogMessageBehaviorImpl(
            thresholdCheck,
            remoteSupplier = { getConfig().logConfig }
        )

    override val anrBehavior: AnrBehavior =
        AnrBehaviorImpl(
            thresholdCheck,
            localSupplier = localConfig.sdkConfig::anr,
            remoteSupplier = { getConfig().anrConfig }
        )

    override val sessionBehavior: SessionBehavior =
        SessionBehaviorImpl(
            thresholdCheck,
            localSupplier = localConfig.sdkConfig::sessionConfig,
            remoteSupplier = { getConfig() }
        )

    override val networkBehavior: NetworkBehavior =
        NetworkBehaviorImpl(
            thresholdCheck = thresholdCheck,
            localSupplier = localConfig::sdkConfig,
            remoteSupplier = remoteSupplier
        )

    override val startupBehavior: StartupBehavior =
        StartupBehaviorImpl(
            thresholdCheck = thresholdCheck,
            localSupplier = localConfig.sdkConfig::startupMoment
        )

    override val dataCaptureEventBehavior: DataCaptureEventBehavior = DataCaptureEventBehaviorImpl(
        thresholdCheck = thresholdCheck,
        remoteSupplier = remoteSupplier
    )

    override val sdkModeBehavior: SdkModeBehavior =
        SdkModeBehaviorImpl(
            thresholdCheck = thresholdCheck,
            localSupplier = { localConfig },
            remoteSupplier = remoteSupplier
        )

    override val sdkEndpointBehavior: SdkEndpointBehavior =
        SdkEndpointBehaviorImpl(
            thresholdCheck = thresholdCheck,
            localSupplier = localConfig.sdkConfig::baseUrls,
        )

    override val appExitInfoBehavior: AppExitInfoBehavior = AppExitInfoBehaviorImpl(
        thresholdCheck = thresholdCheck,
        localSupplier = localConfig.sdkConfig::appExitInfoConfig,
        remoteSupplier = remoteSupplier
    )

    override val networkSpanForwardingBehavior: NetworkSpanForwardingBehavior =
        NetworkSpanForwardingBehaviorImpl(
            thresholdCheck = thresholdCheck,
            remoteSupplier = { getConfig().networkSpanForwardingRemoteConfig }
        )

    override val webViewVitalsBehavior: WebViewVitalsBehavior =
        WebViewVitalsBehaviorImpl(
            thresholdCheck = thresholdCheck,
            remoteSupplier = remoteSupplier
        )

    override val appId: String? by lazy(localConfig::appId)

    /**
     * Schedule an action that loads the config from the cache.
     * This is deferred to lessen its impact upon startup.
     */
    private fun performInitialConfigLoad() {
        backgroundWorker.submit(runnable = ::loadConfigFromCache)
    }

    /**
     * Load Config from cache if present.
     */
    fun loadConfigFromCache() {
        val cachedConfig = remoteConfigSource?.getCachedConfig()
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
                    logger.logWarning(
                        "Failed to load SDK config from the server. " +
                            "Trying again in " + configRetrySafeWindow + " seconds."
                    )
                }
            }
        }
    }

    private fun updateConfig(previousConfig: RemoteConfig, newConfig: RemoteConfig) {
        val b = newConfig != previousConfig
        if (b) {
            configProp = newConfig
            persistConfig()
            // Only notify listeners if the config has actually changed value
            notifyListeners()
        }
    }

    private fun persistConfig() {
        // TODO: future get rid of these prefs from PrefService entirely?
        preferencesService.sdkDisabled = sdkModeBehavior.isSdkDisabled()
        preferencesService.backgroundActivityEnabled = backgroundActivityBehavior.isBackgroundActivityCaptureEnabled()
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
        foregroundAction()
    }

    override val appFramework: AppFramework = localConfig.sdkConfig.appFramework?.let {
        AppFramework.fromString(it)
    } ?: suppliedFramework

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

    override fun hasValidRemoteConfig(): Boolean = !configRequiresRefresh()
    override fun isAppExitInfoCaptureEnabled(): Boolean {
        return appExitInfoBehavior.isAeiCaptureEnabled()
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
