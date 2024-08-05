package io.embrace.android.embracesdk.internal.capture.startup

/**
 * Service to track the SDK startup time.
 */
public interface StartupService {

    /**
     * Sets the SDK startup info. This is called when the SDK is initialized.
     */
    public fun setSdkStartupInfo(
        startTimeMs: Long,
        endTimeMs: Long,
        endedInForeground: Boolean,
        threadName: String
    )

    /**
     * Returns the SDK startup duration. This is called when the session ends.
     */
    public fun getSdkStartupDuration(coldStart: Boolean): Long?

    /**
     * The epoch time in milliseconds of when the SDK startup began
     */
    public fun getSdkInitStartMs(): Long?

    /**
     * The epoch time in milliseconds of when the SDK startup finished
     */
    public fun getSdkInitEndMs(): Long?

    /**
     * Returns true if the SDK init ended when the app is in the foreground, false if in the background, null if startup info not recorded
     */
    public fun endedInForeground(): Boolean?

    /**
     * Returns the name of the thread on which the SDK init was run. Returns null if startup info was not recorded yet.
     */
    public fun getInitThreadName(): String?
}
