package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.api.UserSessionApi
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.embraceImplInject

internal class UserSessionApiDelegate(
    bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker,
) : UserSessionApi {

    private val userSessionPropertiesService by embraceImplInject(sdkCallChecker) {
        bootstrapper.essentialServiceModule.userSessionPropertiesService
    }
    private val sessionOrchestrator by embraceImplInject(sdkCallChecker) {
        bootstrapper.userSessionOrchestrationModule.sessionOrchestrator
    }

    /**
     * Adds a property to the current session.
     */
    override fun addUserSessionProperty(key: String, value: String, permanent: Boolean): Boolean {
        if (sdkCallChecker.check("add_session_property")) {
            return userSessionPropertiesService?.addProperty(key, value, permanent) ?: false
        }
        return false
    }

    /**
     * Removes a property from the current session.
     */
    override fun removeUserSessionProperty(key: String): Boolean {
        if (sdkCallChecker.check("remove_session_property")) {
            return userSessionPropertiesService?.removeProperty(key) ?: false
        }
        return false
    }

    /**
     * Ends the current session and starts a new one.
     *
     * Cleans all the user info on the device.
     */
    override fun endUserSession(clearUserInfo: Boolean) {
        if (sdkCallChecker.check("end_session")) {
            sessionOrchestrator?.endSessionWithManual(clearUserInfo)
        }
    }
}
