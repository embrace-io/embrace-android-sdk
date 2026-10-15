package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.api.UserApi
import io.embrace.android.embracesdk.internal.injection.ModuleGraph

internal class UserApiDelegate(
    private val moduleGraph: ModuleGraph,
) : UserApi {

    private val userService get() = moduleGraph.essentialServiceModule.userService

    override fun setUserIdentifier(userId: String?) {
        userService.setUserIdentifier(userId)
    }

    override fun clearUserIdentifier() {
        userService.clearUserIdentifier()
    }

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun setUserEmail(email: String?) {
        userService.setUserEmail(email)
    }

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun clearUserEmail() {
        userService.clearUserEmail()
    }

    override fun addUserPersona(persona: String) {
        userService.addUserPersona(persona)
    }

    /**
     * Clears the custom user persona, if it is set.
     *
     * @param persona the persona to clear
     */
    override fun clearUserPersona(persona: String) {
        userService.clearUserPersona(persona)
    }

    /**
     * Clears all custom user personas from the user.
     */
    override fun clearAllUserPersonas() {
        userService.clearAllUserPersonas()
    }

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun setUsername(username: String?) {
        userService.setUsername(username)
    }

    @Deprecated("Use discouraged. Personal identifying information shouldn't be stored in telemetry.")
    override fun clearUsername() {
        userService.clearUsername()
    }
}
