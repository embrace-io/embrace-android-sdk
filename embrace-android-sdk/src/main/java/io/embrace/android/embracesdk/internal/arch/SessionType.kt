package io.embrace.android.embracesdk.internal.arch

import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * The type of session that contains the data. Currently this is either a session (foreground)
 * or a background activity (background).
 */
@InternalApi
public enum class SessionType {

    /**
     * A period of time where the app was in the foreground.
     */
    FOREGROUND,

    /**
     * A period of time where the app was in the background.
     */
    BACKGROUND
}
