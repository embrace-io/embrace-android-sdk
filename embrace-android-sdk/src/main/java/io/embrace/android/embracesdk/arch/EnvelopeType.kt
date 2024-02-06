package io.embrace.android.embracesdk.arch

/**
 * The type of envelope that contains the data. Currently this is either a session or a background
 * activity.
 */
internal enum class EnvelopeType {

    /**
     * A period of time where the app was in the foreground.
     */
    SESSION,

    /**
     * A period of time where the app was in the background.
     */
    BACKGROUND_ACTIVITY
}
