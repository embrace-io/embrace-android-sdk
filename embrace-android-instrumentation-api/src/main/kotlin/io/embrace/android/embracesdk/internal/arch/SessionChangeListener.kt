package io.embrace.android.embracesdk.internal.arch

fun interface SessionChangeListener {

    /**
     * Called when the 'session' has changed. This is called _after_ the previous session has
     * ended.
     */
    fun onPostSessionChange()
}
