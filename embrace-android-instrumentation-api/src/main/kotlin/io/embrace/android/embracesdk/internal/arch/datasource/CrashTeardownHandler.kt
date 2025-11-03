package io.embrace.android.embracesdk.internal.arch.datasource

/**
 * Handles teardown of resources/persistence of telemetry in the event of a JVM crash.
 */
fun interface CrashTeardownHandler {

    /**
     * Performs necessary handling to avoid data loss/resource leakage in the event of a JVM crash.
     * Code must be synchronous and should aim to be as simple as possible to avoid crashing/hanging
     * the app.
     */
    fun handleCrash(crashId: String)
}
