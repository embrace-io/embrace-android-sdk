package io.embrace.android.embracesdk.internal.capture.session

public interface SessionPropertiesService {

    /**
     * Annotates the session with a new property. Use this to track permanent and ephemeral
     * features of the session. A permanent property is added to all sessions submitted from this
     * device, use this for properties such as work site, building, owner. A non-permanent property
     * is added to only the currently active session.
     *
     * The default maximum is 10 total properties in a session.
     *
     * @param originalKey       The key for this property, must be unique within session properties
     * @param originalValue     The value to store for this property
     * @param permanent If true the property is applied to all sessions going forward, persist
     * through app launches.
     * @return A boolean indicating whether the property was added or not
     */
    public fun addProperty(originalKey: String, originalValue: String, permanent: Boolean): Boolean

    /**
     * Removes a property from the session. If that property was permanent then it is removed from
     * all future sessions as well.
     *
     * @param originalKey the key to be removed
     */
    public fun removeProperty(originalKey: String): Boolean

    /**
     * Get a read-only representation of the currently set session properties. You can query and
     * read from this representation however setting values in this object will not modify the
     * actual properties in the session. To modify session properties see addProperty.
     */
    public fun getProperties(): Map<String, String>

    /**
     * Populate the current session with the existing session properties
     */
    public fun populateCurrentSession(): Boolean
}
