package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.api.SessionApi
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.embraceImplInject

internal class SessionApiDelegate(
    bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker,
) : SessionApi {

    private val sessionPropertiesService by embraceImplInject(sdkCallChecker) {
        bootstrapper.essentialServiceModule.sessionPropertiesService
    }
    private val sessionOrchestrator by embraceImplInject(sdkCallChecker) {
        bootstrapper.sessionOrchestrationModule.sessionOrchestrator
    }

    /**
     * Adds a property to the current session.
     */
    override fun addSessionProperty(key: String, value: String, permanent: Boolean): Boolean {
        if (sdkCallChecker.check("add_session_property")) {
            return sessionPropertiesService?.addProperty(key, value, permanent)
                .apply {
                    sessionOrchestrator?.reportBackgroundActivityStateChange()
                } ?: false
        }
        return false
    }

    /**
     * Removes a property from the current session.
     */
    override fun removeSessionProperty(key: String): Boolean {
        if (sdkCallChecker.check("remove_session_property")) {
            return sessionPropertiesService?.removeProperty(key).apply {
                sessionOrchestrator?.reportBackgroundActivityStateChange()
            } ?: false
        }
        return false
    }

    override fun endSession() = endSession(false)

    /**
     * Ends the current session and starts a new one.
     *
     * Cleans all the user info on the device.
     */
    override fun endSession(clearUserInfo: Boolean) {
        if (sdkCallChecker.check("end_session")) {
            sessionOrchestrator?.endSessionWithManual(clearUserInfo)
        }
    }
}
