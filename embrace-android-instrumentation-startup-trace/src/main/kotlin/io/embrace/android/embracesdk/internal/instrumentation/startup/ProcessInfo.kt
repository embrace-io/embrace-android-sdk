package io.embrace.android.embracesdk.internal.instrumentation.startup

/**
 * Runtime-agnostic interface for getting information from the Android Process API
 */
internal interface ProcessInfo {
    /**
     * Return the best-available estimated time for the when the app process was requested to fork
     */
    fun startRequestedTimeMs(): Long?

    /**
     * Return why the OS started this app process, as one of the
     * [io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EmbStartupLaunchReasonValues], or null if the platform does not
     * report it or we cannot confirm that what it reports describes this process. Note this describes the creation of the process
     * rather than what the user did to launch the app: a process forked to handle a push message and subsequently used for a user
     * launch reports a launch reason of "push".
     */
    fun launchReason(): String?

    /**
     * Work out [launchReason] and hold onto it, so that the value is already known by the time the startup trace is recorded. The
     * platform only lets us read start information that has not been narrowed to a single process, so the further into startup we
     * leave the read, the more opportunity there is for another start belonging to this app to have been logged over ours. Resolving
     * it as soon as the SDK starts keeps that window to the few milliseconds between the process being forked and the SDK
     * initialising, rather than letting it grow with the length of the startup being measured.
     *
     * Safe to call from a background thread, and safe to call more than once. [launchReason] falls back to doing the work itself if
     * this was never called.
     */
    fun prefetchLaunchReason()
}
