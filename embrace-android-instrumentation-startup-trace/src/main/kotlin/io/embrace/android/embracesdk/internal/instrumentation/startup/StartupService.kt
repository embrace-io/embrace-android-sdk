package io.embrace.android.embracesdk.internal.instrumentation.startup

import io.embrace.android.embracesdk.internal.arch.state.ProcessState

/**
 * Service to track the SDK startup time.
 */
interface StartupService {

    /**
     * Sets the SDK startup info. This is called when the SDK is initialized.
     */
    fun setSdkStartupInfo(
        startTimeMs: Long,
        endTimeMs: Long,
        endState: ProcessState,
        threadName: String,
        sdkInitDurations: Map<String, Long> = emptyMap(),
    )

    /**
     * Records a private span representing the timing of SDK initialization. Does nothing if
     * [setSdkStartupInfo] has not been called, and will only ever record one span regardless
     * of the number of invocations.
     */
    fun recordSdkInitSpan()

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
     * Returns null if the counter could not be determined.
     */
    fun getAppVersionStartupCounter(): Int?

    /**
     * Durations in milliseconds of the instrumented SDK init sections, keyed by section name.
     * Empty until startup info is recorded.
     */
    fun getSdkInitDurations(): Map<String, Long>
}

/**
 * Add an entry to the map given a map of sdk init durations where the key name comprises the
 * section name with an additional suffix.
 */
internal fun MutableMap<String, String>.putSdkInitDurations(durations: Map<String, Long>) {
    durations.forEach { (sectionName, durationMs) ->
        put("$sectionName-duration-ms", durationMs.toString())
    }
}
