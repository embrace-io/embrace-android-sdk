package io.embrace.android.embracesdk.internal.api

/**
 * The public API that is used to interact with sessions.
 */
internal interface SessionApi {

    /**
     * Adds a property to the current session, overwriting any previous property set with the given key. If a permanent property
     * already exists with the given name and a non-permanent one is to be added, the permanent one will be removed (and vice versa).
     *
     * @param key       The case-sensitive key to be used for this property. The maximum length for this is 128 characters. A key passed in
     * that exceeds the maximum length will be truncated.
     * @param value     The value associated with the given key. The maximum length for this is 1024 characters. A value passed in that
     * exceeds the maximum length will be truncated.
     * @param permanent True if this property should be added to subsequent sessions going forward, persisting through app launches.
     *
     * @return True if the property was successfully added. Reasons this may fail include an invalid key or value, or if the
     * session has exceeded its total properties limit.
     */
    fun addSessionProperty(
        key: String,
        value: String,
        permanent: Boolean
    ): Boolean

    /**
     * Removes a property from the current session.
     *
     * @return true if a property with that name had previously existed.
     */
    fun removeSessionProperty(key: String): Boolean

    /**
     * Retrieves a map of the current session properties.
     *
     * @return a new immutable map containing the current session properties, or null if the SDK has not been started or has been stopped.
     */
    fun getSessionProperties(): Map<String, String>?

    /**
     * Ends the current session and starts a new one.
     */
    fun endSession()

    /**
     * Ends the current session and starts a new one.
     *
     * @param clearUserInfo Pass in true to clear all user info set on this device.
     */
    fun endSession(clearUserInfo: Boolean)
}
