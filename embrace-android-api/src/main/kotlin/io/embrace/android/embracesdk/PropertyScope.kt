package io.embrace.android.embracesdk

/**
 * Defines the lifetime/persistence scope of a user session property.
 */
public enum class PropertyScope {

    /**
     * Cleared when the current user session ends.
     */
    UserSession,

    /**
     * Cleared when the process terminates.
     */
    Process,

    /**
     * Survives process death and is applied to all future sessions until explicitly removed.
     */
    Permanent,
    ;

    public companion object {
        @Deprecated("Use UserSession", ReplaceWith("PropertyScope.UserSession"))
        @JvmField
        public val USER_SESSION: PropertyScope = UserSession

        @Deprecated("Use Process", ReplaceWith("PropertyScope.Process"))
        @JvmField
        public val PROCESS: PropertyScope = Process

        @Deprecated("Use Permanent", ReplaceWith("PropertyScope.Permanent"))
        @JvmField
        public val PERMANENT: PropertyScope = Permanent
    }
}
