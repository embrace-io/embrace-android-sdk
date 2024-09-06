package io.embrace.android.embracesdk.internal.capture.crash

/**
 * Interface for handling crash teardown.
 */
interface CrashTeardownHandler {
    fun handleCrash(crashId: String)
}
