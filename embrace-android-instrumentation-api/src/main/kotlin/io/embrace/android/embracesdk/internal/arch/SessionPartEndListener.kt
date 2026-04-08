package io.embrace.android.embracesdk.internal.arch

fun interface SessionPartEndListener {
    /**
     * Called when a session is about to end.
     */
    fun onPreSessionEnd()
}
