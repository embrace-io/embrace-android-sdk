package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.api.UserApi
import io.embrace.android.embracesdk.internal.capture.user.UserService

internal class UserApiDelegate(
    private val userService: Lazy<UserService>,
) : UserApi {

    override fun setUserIdentifier(userId: String?) {
        userService.value.setUserIdentifier(userId)
    }

    override fun clearUserIdentifier() {
        userService.value.clearUserIdentifier()
    }

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun setUserEmail(email: String?) {
        userService.value.setUserEmail(email)
    }

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun clearUserEmail() {
        userService.value.clearUserEmail()
    }

    override fun addUserPersona(persona: String) {
        userService.value.addUserPersona(persona)
    }

    /**
     * Clears the custom user persona, if it is set.
     *
     * @param persona the persona to clear
     */
    override fun clearUserPersona(persona: String) {
        userService.value.clearUserPersona(persona)
    }

    /**
     * Clears all custom user personas from the user.
     */
    override fun clearAllUserPersonas() {
        userService.value.clearAllUserPersonas()
    }

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun setUsername(username: String?) {
        userService.value.setUsername(username)
    }

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun clearUsername() {
        userService.value.clearUsername()
    }
}
