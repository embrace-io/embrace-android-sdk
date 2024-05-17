package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.UserApi
import io.embrace.android.embracesdk.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.injection.embraceImplInject

internal class UserApiDelegate(
    bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker
) : UserApi {

    private val userService by embraceImplInject(sdkCallChecker) { bootstrapper.essentialServiceModule.userService }
    private val ndkService by embraceImplInject(sdkCallChecker) { bootstrapper.nativeModule.ndkService }

    /**
     * Sets the user ID. This would typically be some form of unique identifier such as a UUID or
     * database key for the user.
     *
     * @param userId the unique identifier for the user
     */
    override fun setUserIdentifier(userId: String?) {
        if (sdkCallChecker.check("set_user_identifier")) {
            userService?.setUserIdentifier(userId)
            // Update user info in NDK service
            ndkService?.onUserInfoUpdate()
        }
    }

    /**
     * Clears the currently set user ID. For example, if the user logs out.
     */
    override fun clearUserIdentifier() {
        if (sdkCallChecker.check("clear_user_identifier")) {
            userService?.clearUserIdentifier()
            ndkService?.onUserInfoUpdate()
        }
    }

    /**
     * Sets the current user's email address.
     *
     * @param email the email address of the current user
     */
    override fun setUserEmail(email: String?) {
        if (sdkCallChecker.check("set_user_email")) {
            userService?.setUserEmail(email)
            // Update user info in NDK service
            ndkService?.onUserInfoUpdate()
        }
    }

    /**
     * Clears the currently set user's email address.
     */
    override fun clearUserEmail() {
        if (sdkCallChecker.check("clear_user_email")) {
            userService?.clearUserEmail()
            // Update user info in NDK service
            ndkService?.onUserInfoUpdate()
        }
    }

    /**
     * Sets this user as a paying user. This adds a persona to the user's identity.
     */
    override fun setUserAsPayer() {
        if (sdkCallChecker.check("set_user_as_payer")) {
            userService?.setUserAsPayer()
            // Update user info in NDK service
            ndkService?.onUserInfoUpdate()
        }
    }

    /**
     * Clears this user as a paying user. This would typically be called if a user is no longer
     * paying for the service and has reverted back to a basic user.
     */
    override fun clearUserAsPayer() {
        if (sdkCallChecker.check("clear_user_as_payer")) {
            userService?.clearUserAsPayer()
            // Update user info in NDK service
            ndkService?.onUserInfoUpdate()
        }
    }

    /**
     * Sets a custom user persona. A persona is a trait associated with a given user.
     *
     * @param persona the persona to set
     */
    override fun addUserPersona(persona: String) {
        if (sdkCallChecker.check("add_user_persona")) {
            userService?.addUserPersona(persona)
            // Update user info in NDK service
            ndkService?.onUserInfoUpdate()
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
            // Update user info in NDK service
            ndkService?.onUserInfoUpdate()
        }
    }

    /**
     * Clears all custom user personas from the user.
     */
    override fun clearAllUserPersonas() {
        if (sdkCallChecker.check("clear_user_personas")) {
            userService?.clearAllUserPersonas()
            // Update user info in NDK service
            ndkService?.onUserInfoUpdate()
        }
    }

    /**
     * Sets the username of the currently logged in user.
     *
     * @param username the username to set
     */
    override fun setUsername(username: String?) {
        if (sdkCallChecker.check("set_username")) {
            userService?.setUsername(username)
            // Update user info in NDK service
            ndkService?.onUserInfoUpdate()
        }
    }

    /**
     * Clears the username of the currently logged in user, for example if the user has logged out.
     */
    override fun clearUsername() {
        if (sdkCallChecker.check("clear_username")) {
            userService?.clearUsername()
            // Update user info in NDK service
            ndkService?.onUserInfoUpdate()
        }
    }
}
