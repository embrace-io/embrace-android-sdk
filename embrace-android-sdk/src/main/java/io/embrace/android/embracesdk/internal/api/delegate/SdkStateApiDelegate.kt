package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.internal.api.SdkStateApi
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.embraceImplInject
import java.util.regex.Pattern

internal class SdkStateApiDelegate(
    bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker
) : SdkStateApi {

    private val logger = bootstrapper.initModule.logger
    private val sessionIdTracker by embraceImplInject(sdkCallChecker) {
        bootstrapper.essentialServiceModule.sessionIdTracker
    }
    private val preferencesService by embraceImplInject(sdkCallChecker) {
        bootstrapper.androidServicesModule.preferencesService
    }
    private val crashVerifier by embraceImplInject(sdkCallChecker) { bootstrapper.crashModule.lastRunCrashVerifier }

    /**
     * Custom app ID that overrides the one specified at build time
     */
    @Volatile
    var customAppId: String? = null

    /**
     * Whether or not the SDK has been started.
     *
     * @return true if the SDK is started, false otherwise
     */
    override val isStarted: Boolean
        get() = sdkCallChecker.started.get()

    /**
     * Sets a custom app ID that overrides the one specified at build time. Must be called before
     * the SDK is started.
     *
     * @param appId custom app ID
     * @return true if the app ID could be set, false otherwise.
     */
    override fun setAppId(appId: String): Boolean {
        if (isStarted) {
            logger.logError("You must set the custom app ID before the SDK is started.", null)
            return false
        }
        if (appId.isEmpty()) {
            logger.logError("App ID cannot be null or empty.", null)
            return false
        }
        if (!appIdPattern.matcher(appId).find()) {
            logger.logError(
                "Invalid app ID. Must be a 5-character string with characters from the set [A-Za-z0-9], but it was \"$appId\".",
                null
            )
            return false
        }

        customAppId = appId
        return true
    }

    override val deviceId: String
        get() {
            return if (sdkCallChecker.check("get_device_id")) {
                preferencesService?.deviceIdentifier ?: ""
            } else {
                ""
            }
        }

    override val currentSessionId: String?
        get() {
            val localSessionIdTracker = sessionIdTracker
            if (localSessionIdTracker != null && sdkCallChecker.check("get_current_session_id")) {
                val sessionId = localSessionIdTracker.getActiveSessionId()
                if (sessionId != null) {
                    return sessionId
                } else {
                    logger.logInfo("Session ID is null", null)
                }
            }
            return null
        }

    override val lastRunEndState: Embrace.LastRunEndState
        get() {
            return if (isStarted && crashVerifier != null) {
                if (crashVerifier?.didLastRunCrash() == true) {
                    Embrace.LastRunEndState.CRASH
                } else {
                    Embrace.LastRunEndState.CLEAN_EXIT
                }
            } else {
                Embrace.LastRunEndState.INVALID
            }
        }

    companion object {
        private val appIdPattern: Pattern by lazy { Pattern.compile("^[A-Za-z0-9]{5}$") }
    }
}
