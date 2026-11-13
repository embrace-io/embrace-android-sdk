package io.embrace.android.embracesdk.internal.prefs

/**
 * Orders the prefs migration's writes against the stores giving up on it, so a migrator still running after the
 * stores' deadline can't write over the fresh state they started with.
 */
class PrefsMigrationGate {

    private var abandoned = false

    /**
     * Runs [block] unless [abandon] has been called, and returns whether it ran.
     */
    @Synchronized
    fun commit(block: () -> Unit): Boolean {
        if (abandoned) {
            return false
        }
        block()
        return true
    }

    /**
     * Stops later calls to [commit] and runs [block]. Waits for a [commit] already in progress to finish first.
     */
    @Synchronized
    fun abandon(block: () -> Unit) {
        abandoned = true
        block()
    }
}
