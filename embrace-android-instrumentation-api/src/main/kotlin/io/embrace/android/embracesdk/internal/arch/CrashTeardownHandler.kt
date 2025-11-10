package io.embrace.android.embracesdk.internal.arch

/**
 * Interface for handling crash teardown.
 */
fun interface CrashTeardownHandler {
    fun handleCrash(crashId: String)
}
