package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.SessionApi
import io.embrace.android.embracesdk.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.injection.embraceImplInject

internal class SessionApiDelegate(
    bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker
) : SessionApi {

    private val sessionPropertiesService by embraceImplInject(sdkCallChecker) {
        bootstrapper.sessionModule.sessionPropertiesService
    }
    private val sessionOrchestrator by embraceImplInject(sdkCallChecker) { bootstrapper.sessionModule.sessionOrchestrator }

    /**
     * Adds a property to the current session.
     */
    override fun addSessionProperty(key: String, value: String, permanent: Boolean): Boolean {
        if (sdkCallChecker.check("add_session_property")) {
            return sessionPropertiesService?.addProperty(key, value, permanent) ?: false
        }
        return false
    }

    /**
     * Removes a property from the current session.
     */
    override fun removeSessionProperty(key: String): Boolean {
        if (sdkCallChecker.check("remove_session_property")) {
            return sessionPropertiesService?.removeProperty(key) ?: false
        }
        return false
    }

    override fun getSessionProperties(): Map<String, String>? {
        if (sdkCallChecker.check("get_session_properties")) {
            return sessionPropertiesService?.getProperties()
        }
        return null
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
