package io.embrace.android.embracesdk.internal.capture.startup

/**
 * Service to track the SDK startup time.
 */
internal interface StartupService {

    /**
     * Sets the SDK startup info. This is called when the SDK is initialized.
     */
    fun setSdkStartupInfo(
        startTimeMs: Long,
        endTimeMs: Long,
        endedInForeground: Boolean,
        threadName: String
    )

    /**
     * Returns the SDK startup duration. This is called when the session ends.
     */
    fun getSdkStartupDuration(coldStart: Boolean): Long?

    /**
     * The epoch time in milliseconds of when the SDK startup began
     */
    fun getSdkInitStartMs(): Long?

    /**
     * The epoch time in milliseconds of when the SDK startup finished
     */
    fun getSdkInitEndMs(): Long?

    /**
     * Returns true if the SDK init ended when the app is in the foreground, false if in the background, null if startup info not recorded
     */
    fun endedInForeground(): Boolean?

    /**
     * Returns the name of the thread on which the SDK init was run. Returns null if startup info was not recorded yet.
     */
    fun getInitThreadName(): String?
}
