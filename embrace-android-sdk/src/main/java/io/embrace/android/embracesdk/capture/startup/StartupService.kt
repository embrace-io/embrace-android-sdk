package io.embrace.android.embracesdk.capture.startup

/**
 * Service to track the SDK startup time.
 */
internal interface StartupService {

    /**
     * Sets the SDK startup info. This is called when the SDK is initialized.
     */
    fun setSdkStartupInfo(startTimeMs: Long, endTimeMs: Long)

    /**
     * Returns the SDK startup info. This is called when the session ends.
     */
    fun getSdkStartupInfo(coldStart: Boolean): Long?
}
