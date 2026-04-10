package io.embrace.android.embracesdk.internal.session

/**
 * Listens for events in the 'user session' lifecycle.
 */
internal fun interface UserSessionListener {

    /**
     * Invoked when a new user session has started.
     */
    fun onNewUserSession()
}
