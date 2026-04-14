package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.UserSessionListener
import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * The public API that is used to interact with user sessions.
 */
@InternalApi
public interface UserSessionApi {

    /**
     * Adds a property to the current user session, overwriting any previous property set with the given key. If a property already
     * exists with the given name and a different scope, the existing property will be removed and replaced.
     *
     * @param key       The case-sensitive key to be used for this property. The maximum length for this is 128 characters. A key passed in
     * that exceeds the maximum length will be truncated.
     * @param value     The value associated with the given key. The maximum length for this is 1024 characters. A value passed in that
     * exceeds the maximum length will be truncated.
     * @param scope     The lifetime scope of the property. Use [PropertyScope.USER_SESSION] for properties that are cleared
     * at the end of the session, [PropertyScope.PROCESS] for properties that survive session boundaries but not process
     * death, or [PropertyScope.PERMANENT] for properties that persist through app launches.
     *
     * @return True if the property was successfully added. Reasons this may fail include an invalid key or value, or if the
     * session has exceeded its total properties limit.
     */
    public fun addUserSessionProperty(
        key: String,
        value: String,
        scope: PropertyScope,
    ): Boolean

    /**
     * Removes a property from the current user session.
     *
     * @return true if a property with that name had previously existed.
     */
    public fun removeUserSessionProperty(key: String): Boolean

    /**
     * Ends the current user session and starts a new one.
     *
     * @param clearUserInfo Pass in true to clear all user info set on this device.
     */
    public fun endUserSession(clearUserInfo: Boolean = false)

    /**
     * Registers a listener that is invoked for user session lifecycle events.
     */
    public fun addUserSessionListener(listener: UserSessionListener)
}
