package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.payload.Session.SessionLifeEventType
import java.io.Closeable

internal interface SessionService : Closeable {

    /**
     * Starts a new session.
     *
     * @param coldStart whether this is a cold start of the application
     * @param startType the origin of the event that starts the session.
     */
    fun startSession(coldStart: Boolean, startType: SessionLifeEventType, startTime: Long)

    /**
     * This is responsible for the current session to be cached, ended and sent to the server and
     * immediately starting a new session after that.
     *
     * @param endType the origin of the event that ends the session.
     */
    fun triggerStatelessSessionEnd(endType: SessionLifeEventType)

    /**
     * Handles an uncaught exception, ending the session and saving the session to disk.
     */
    fun handleCrash(crashId: String)

    /**
     * Annotates the session with a new property.  Use this to track permanent and ephemeral
     * features of the session. A permanent property is added to all sessions submitted from this
     * device, use this for properties such as work site, building, owner. A non-permanent property
     * is added to only the currently active session.
     *
     *
     * There is a maximum of 10 total properties in a session.
     *
     * @param key       The key for this property, must be unique within session properties
     * @param value     The value to store for this property
     * @param permanent If true the property is applied to all sessions going forward, persist
     * through app launches.
     * @return A boolean indicating whether the property was added or not
     */
    fun addProperty(key: String, value: String, permanent: Boolean): Boolean

    /**
     * Removes a property from the session.  If that property was permanent then it is removed from
     * all future sessions as well.
     *
     * @param key the key to be removed
     */
    fun removeProperty(key: String): Boolean

    /**
     * Get a read-only representation of the currently set session properties.  You can query and
     * read from this representation however setting values in this object will not modify the
     * actual properties in the session. To modify session properties see addProperty.
     */
    fun getProperties(): Map<String, String>
}
