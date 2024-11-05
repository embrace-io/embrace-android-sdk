package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.api.UserApi
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.embraceImplInject

internal class UserApiDelegate(
    bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker,
) : UserApi {

    private val userService by embraceImplInject(sdkCallChecker) { bootstrapper.essentialServiceModule.userService }

    override fun setUserIdentifier(userId: String?) {
        if (sdkCallChecker.check("set_user_identifier")) {
            userService?.setUserIdentifier(userId)
        }
    }

    override fun clearUserIdentifier() {
        if (sdkCallChecker.check("clear_user_identifier")) {
            userService?.clearUserIdentifier()
        }
    }

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun setUserEmail(email: String?) {
        if (sdkCallChecker.check("set_user_email")) {
            userService?.setUserEmail(email)
        }
    }

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
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

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun setUsername(username: String?) {
        if (sdkCallChecker.check("set_username")) {
            userService?.setUsername(username)
        }
    }

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun clearUsername() {
        if (sdkCallChecker.check("clear_username")) {
            userService?.clearUsername()
        }
    }
}
