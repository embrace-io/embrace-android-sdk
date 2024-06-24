package io.embrace.android.embracesdk.payload

internal enum class ApplicationState {

    /**
     * Signals to the API that the application was in the foreground.
     */
    FOREGROUND,

    /**
     * Signals to the API that this is a background session.
     */
    BACKGROUND
}
