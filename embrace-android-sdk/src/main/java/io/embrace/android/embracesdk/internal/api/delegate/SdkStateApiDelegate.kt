package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.LastRunEndState
import io.embrace.android.embracesdk.internal.api.SdkStateApi
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.embraceImplInject
import java.util.regex.Pattern

internal class SdkStateApiDelegate(
    bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker,
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
                }
            }
            return null
        }

    override val lastRunEndState: LastRunEndState
        get() {
            return if (isStarted && crashVerifier != null) {
                if (crashVerifier?.didLastRunCrash() == true) {
                    LastRunEndState.CRASH
                } else {
                    LastRunEndState.CLEAN_EXIT
                }
            } else {
                LastRunEndState.INVALID
            }
        }

    private companion object {
        val appIdPattern: Pattern by lazy { Pattern.compile("^[A-Za-z0-9]{5}$") }
    }
}
