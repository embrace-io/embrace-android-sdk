package io.embrace.android.embracesdk.internal.instrumentation.startup

import io.embrace.android.embracesdk.internal.arch.state.ProcessState

/**
 * Service to track the SDK startup time.
 */
interface StartupService {

    /**
     * Sets the SDK startup info. This is called when the SDK is initialized.
     *
     * [attributesProvider] is a deferred supplier of the SDK init attributes (section durations,
     * perf counters) attached to the spans that describe the init. It is invoked when a span is
     * recorded - i.e. off the startup hot path - so suppliers may do work (parsing, binder
     * calls) that must not happen during init itself.
     */
    fun setSdkStartupInfo(
        startTimeMs: Long,
        endTimeMs: Long,
        endState: ProcessState,
        threadName: String,
        attributesProvider: (() -> Map<String, String>)? = null,
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
     * Extra attributes to be attached to telemetry tracking SDK startup. Empty until startup info is
     * recorded. Invokes the provider given to [setSdkStartupInfo], which could require computation or
     * code execution that could be slow. Do not call this in perf-sensitive places. The result is
     * memoized on first use, so the provider runs at most once and every caller sees the same
     * attributes.
     */
    fun getSdkInitAttributes(): Map<String, String>
}

/**
 * Converts SDK init section durations to span attributes, keyed by section name with the `-duration-ms` suffix.
 */
fun Map<String, Long>.toSdkInitDurationAttributes(): Map<String, String> =
    entries.associate { (sectionName, durationMs) ->
        "$sectionName-duration-ms" to durationMs.toString()
    }
