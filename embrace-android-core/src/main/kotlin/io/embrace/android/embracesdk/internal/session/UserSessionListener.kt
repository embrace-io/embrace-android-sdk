package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.SessionStateEvent

/**
 * Listens for events in the 'user session' lifecycle.
 */
fun interface UserSessionListener {

    fun onSessionStateEvent(event: SessionStateEvent)
}
