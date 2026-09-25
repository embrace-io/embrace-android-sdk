package io.embrace.android.embracesdk.benchmark.scenario

/**
 * Backgrounds the app and brings it back, so that the SDK ends one session part and starts another.
 */
fun interface ForegroundCycler {

    /**
     * Blocks until the app has spent [ms] in the background and is foregrounded once more.
     */
    fun cycle(ms: Long)
}
