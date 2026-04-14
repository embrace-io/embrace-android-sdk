package io.embrace.android.embracesdk

/**
 * Defines the lifetime/persistence scope of a user session property.
 */
public enum class PropertyScope {

    /**
     * Cleared when the current user session ends.
     */
    USER_SESSION,

    /**
     * Cleared when the process terminates.
     */
    PROCESS,

    /**
     * Survives process death and is applied to all future sessions until explicitly removed.
     */
    PERMANENT,
}
