package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * The public API that is used to set user information.
 */
@InternalApi
public interface UserApi {

    /**
     * Sets the user ID. This would typically be some form of unique identifier such as a UUID or database key for the user.
     * This ID will persist across app launches until it is explicitly removed using [.clearUserIdentifier]
     * or otherwise cleared.
     *
     * @param userId the unique identifier for the user
     */
    public fun setUserIdentifier(userId: String?)

    /**
     * Clears the currently set user ID. For example, if the user logs out.
     */
    public fun clearUserIdentifier()

    /**
     * Sets the current user's email address.
     *
     * @param email the email address of the current user
     */
    public fun setUserEmail(email: String?)

    /**
     * Clears the currently set user's email address.
     */
    public fun clearUserEmail()

    /**
     * Sets this user as a paying user. This adds a persona to the user's identity.
     */
    public fun setUserAsPayer()

    /**
     * Clears this user as a paying user. This would typically be called if a user is no longer
     * paying for the service and has reverted back to a basic user.
     */
    public fun clearUserAsPayer()

    /**
     * Adds a custom user persona. A persona is a trait associated with a given user. A maximum
     * of 10 personas can be set.
     *
     * @param persona the persona to set
     */
    public fun addUserPersona(persona: String)

    /**
     * Clears the custom user persona, if it is set.
     *
     * @param persona the persona to clear
     */
    public fun clearUserPersona(persona: String)

    /**
     * Clears all custom user personas from the user.
     */
    public fun clearAllUserPersonas()

    /**
     * Sets the username of the currently logged in user.
     *
     * @param username the username to set
     */
    public fun setUsername(username: String?)

    /**
     * Clears the username of the currently logged in user, for example if the user has logged out.
     */
    public fun clearUsername()
}
