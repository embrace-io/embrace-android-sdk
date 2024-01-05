package io.embrace.android.embracesdk.session.properties

internal interface SessionPropertiesService {

    /**
     * Annotates the session with a new property. Use this to track permanent and ephemeral
     * features of the session. A permanent property is added to all sessions submitted from this
     * device, use this for properties such as work site, building, owner. A non-permanent property
     * is added to only the currently active session.
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
     * Removes a property from the session. If that property was permanent then it is removed from
     * all future sessions as well.
     *
     * @param key the key to be removed
     */
    fun removeProperty(key: String): Boolean

    /**
     * Get a read-only representation of the currently set session properties. You can query and
     * read from this representation however setting values in this object will not modify the
     * actual properties in the session. To modify session properties see addProperty.
     */
    fun getProperties(): Map<String, String>
}
