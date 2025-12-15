package io.embrace.android.embracesdk.internal.arch

fun interface SessionChangeListener {

    /**
     * Called when the 'session' has changed. This is called _after_ the previous session has
     * ended. It should be invoked even if the ending did not result in a new session being created.
     */
    fun onPostSessionChange()
}
