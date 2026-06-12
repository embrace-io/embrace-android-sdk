package io.embrace.android.embracesdk.internal.arch.startup

/**
 * Determines the [StartupType] for the current app instance based on heuristics
 */
interface StartupClassifier {

    /**
     * The type of startup for this app instance. Returns null if it has not collected enough signals to determine it.
     */
    fun startupType(): StartupType?

    /**
     * Determine the startup type once there is indication that the app process is being created to back an app instance that will be used
     * by a user, rather than do work on the background like processing a notification. The signals it takes in include [sdkInitEndMs]
     * (when the Embrace SDK was started), [appInitEndMs] (when the Application object creation was completed), and [postAppInitTimeMs]
     * (the first indication that the process was created to power an app instance for a user).
     */
    fun evaluateStartup(
        sdkInitEndMs: Long?,
        appInitEndMs: Long?,
        postAppInitTimeMs: Long,
    )

    /**
     * Signals to the classifier that the process launch is assumed to be for some background task that isn't explicitly triggered by
     * a user.
     */
    fun assumeBackgroundStartup()
}

/**
 * The maximum time between the end of app init to when additional init activity was detected that we consider the startup
 * to be cold. Otherwise, we assume the process was created in the background prior to a user app launch, so we classify the
 * startup as being a warm one.
 */
const val MAX_COLD_STARTUP_INIT_GAP_MS: Long = 2000L
