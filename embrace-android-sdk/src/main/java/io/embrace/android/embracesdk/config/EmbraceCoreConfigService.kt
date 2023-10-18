package io.embrace.android.embracesdk.config

import io.embrace.android.embracesdk.config.behavior.BehaviorThresholdCheck
import io.embrace.android.embracesdk.config.behavior.SdkAppBehavior
import io.embrace.android.embracesdk.config.behavior.SdkEndpointBehavior
import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.prefs.PreferencesService
import io.embrace.android.embracesdk.session.ActivityListener

/**
 * Loads basic configuration from the LocalConfig file.
 */
internal class EmbraceCoreConfigService @JvmOverloads constructor(
    private val localConfig: LocalConfig,
    private val preferencesService: PreferencesService,
    private val logger: InternalEmbraceLogger,
    internal val thresholdCheck: BehaviorThresholdCheck = BehaviorThresholdCheck(preferencesService::deviceIdentifier)
) : CoreConfigService, ActivityListener {

    override val sdkAppBehavior: SdkAppBehavior =
        SdkAppBehavior(
            thresholdCheck = thresholdCheck,
            localSupplier = { localConfig }
        )

    override val sdkEndpointBehavior: SdkEndpointBehavior =
        SdkEndpointBehavior(
            thresholdCheck = thresholdCheck,
            localSupplier = localConfig.sdkConfig::baseUrls,
        )

    override fun close() {
        logger.logDebug("Shutting down EmbraceCoreConfigService")
    }
}
