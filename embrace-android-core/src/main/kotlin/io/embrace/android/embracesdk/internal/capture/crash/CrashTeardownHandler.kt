package io.embrace.android.embracesdk.internal.capture.crash

/**
 * Interface for handling crash teardown.
 */
public interface CrashTeardownHandler {
    public fun handleCrash(crashId: String)
}
