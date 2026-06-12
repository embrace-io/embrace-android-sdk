package io.embrace.android.embracesdk.internal.arch.startup

/**
 * Classification of the type of app launch that created the current app process and instance
 */
enum class StartupType {

    /**
     * The app was launched and a new process was created as a result.
     */
    COLD,

    /**
     * The app was launched with an existing app process already in the background.
     */
    WARM,

    /**
     * The app process was created to serve a background job without any indication that it will be used by a user (e.g. an Activity
     * being created)
     */
    BACKGROUND,
}
