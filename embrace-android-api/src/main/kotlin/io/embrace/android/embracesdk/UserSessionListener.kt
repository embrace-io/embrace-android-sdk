package io.embrace.android.embracesdk

/**
 * Listens for events in the user session lifecycle.
 *
 * @see SessionStateEvent
 */
public fun interface UserSessionListener {

    /**
     * Invoked when a [SessionStateEvent] occurs in the user session lifecycle.
     */
    public fun onSessionStateEvent(event: SessionStateEvent)
}
