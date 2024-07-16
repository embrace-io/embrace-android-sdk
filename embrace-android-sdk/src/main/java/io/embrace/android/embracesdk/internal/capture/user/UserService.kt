package io.embrace.android.embracesdk.internal.capture.user

import io.embrace.android.embracesdk.payload.UserInfo

/**
 * Manages user information, allowing the setting and removing of various user attributes.
 */
internal interface UserService {

    /**
     * Gets information on the current user of the device. This is sent with all events and sessions.
     *
     * @return the current user information
     */
    fun getUserInfo(): UserInfo

    /**
     * Cleans the user info.
     */
    fun clearAllUserInfo()

    /**
     * Gets user information from persistent storage.
     *
     * @return the current user information from persistent storage
     */
    fun loadUserInfoFromDisk(): UserInfo?

    /**
     * Sets the user's ID. This could be a UUID, for example.
     *
     * @param userId the user's unique identifier
     */
    fun setUserIdentifier(userId: String?)

    /**
     * Removes the user's unique identifier.
     */
    fun clearUserIdentifier()

    /**
     * Sets the user's email address.
     *
     * @param email the user's email address
     */
    fun setUserEmail(email: String?)

    /**
     * Removes the user's email address.
     */
    fun clearUserEmail()

    /**
     * Sets the user as a paying user, attaching the paying persona to the user.
     */
    fun setUserAsPayer()

    /**
     * Unsets the user as a paying user, removing the paying persona from the user.
     */
    fun clearUserAsPayer()

    /**
     * Attaches the specified persona to the user. This can be used for user segmentation.
     *
     * @param persona the persona to attach to the user
     */
    fun addUserPersona(persona: String?)

    /**
     * Removes the specified user persona from the user.
     *
     * @param persona the persona to remove from the user
     */
    fun clearUserPersona(persona: String?)

    /**
     * Clears all personas from the user, except for system personas.
     */
    fun clearAllUserPersonas()

    /**
     * Sets the user's username.
     *
     * @param username the username to set
     */
    fun setUsername(username: String?)

    /**
     * Removes the user's username.
     */
    fun clearUsername()
}
