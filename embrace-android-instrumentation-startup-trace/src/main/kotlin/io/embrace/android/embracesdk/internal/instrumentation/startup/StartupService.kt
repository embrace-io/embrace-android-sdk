package io.embrace.android.embracesdk.internal.instrumentation.startup

import io.embrace.android.embracesdk.internal.arch.state.AppState

/**
 * Service to track the SDK startup time.
 */
interface StartupService {

    /**
     * Sets the SDK startup info. This is called when the SDK is initialized.
     * [appVersionStartupCounter] is the number of SDK startups on the current app version,
     * including this one. Null if the counter could not be determined.
     */
    fun setSdkStartupInfo(
        startTimeMs: Long,
        endTimeMs: Long,
        endState: AppState,
        threadName: String,
        appVersionStartupCounter: Int?,
    )

    /**
     * Returns the SDK startup duration.
     */
    fun getSdkStartupDuration(): Long?

    /**
     * The epoch time in milliseconds of when the SDK startup began
     */
    fun getSdkInitStartMs(): Long?

    /**
     * The epoch time in milliseconds of when the SDK startup finished
     */
    fun getSdkInitEndMs(): Long?

    /**
     * Returns the name of the thread on which the SDK init was run. Returns null if startup info was not recorded yet.
     */
    fun getInitThreadName(): String?

    /**
     * Returns the number of SDK startups on the current app version, including this one.
     * Returns null if startup info was not recorded yet or the counter could not be determined.
     */
    fun getAppVersionStartupCounter(): Int?
}
