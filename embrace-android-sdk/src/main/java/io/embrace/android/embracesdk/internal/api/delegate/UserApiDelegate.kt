package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.api.UserApi
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.embraceImplInject

internal class UserApiDelegate(
    bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker,
) : UserApi {

    private val userService by embraceImplInject(sdkCallChecker) { bootstrapper.essentialServiceModule.userService }

    @Deprecated("Discourage storing personal identifying information in telemetry")
    override fun setUserIdentifier(userId: String?) {
        if (sdkCallChecker.check("set_user_identifier")) {
            userService?.setUserIdentifier(userId)
        }
    }

    @Deprecated("Discourage storing personal identifying information in telemetry")
    override fun clearUserIdentifier() {
        if (sdkCallChecker.check("clear_user_identifier")) {
            userService?.clearUserIdentifier()
        }
    }

    @Deprecated("Discourage storing personal identifying information in telemetry")
    override fun setUserEmail(email: String?) {
        if (sdkCallChecker.check("set_user_email")) {
            userService?.setUserEmail(email)
        }
    }

    @Deprecated("Discourage storing personal identifying information in telemetry")
    override fun clearUserEmail() {
        if (sdkCallChecker.check("clear_user_email")) {
            userService?.clearUserEmail()
        }
    }

    override fun addUserPersona(persona: String) {
        if (sdkCallChecker.check("add_user_persona")) {
            userService?.addUserPersona(persona)
        }
    }

    /**
     * Clears the custom user persona, if it is set.
     *
     * @param persona the persona to clear
     */
    override fun clearUserPersona(persona: String) {
        if (sdkCallChecker.check("clear_user_persona")) {
            userService?.clearUserPersona(persona)
        }
    }

    /**
     * Clears all custom user personas from the user.
     */
    override fun clearAllUserPersonas() {
        if (sdkCallChecker.check("clear_user_personas")) {
            userService?.clearAllUserPersonas()
        }
    }

    @Deprecated("Discourage storing personal identifying information in telemetry")
    override fun setUsername(username: String?) {
        if (sdkCallChecker.check("set_username")) {
            userService?.setUsername(username)
        }
    }

    @Deprecated("Discourage storing personal identifying information in telemetry")
    override fun clearUsername() {
        if (sdkCallChecker.check("clear_username")) {
            userService?.clearUsername()
        }
    }
}
