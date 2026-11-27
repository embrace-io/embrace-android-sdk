package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.SessionStateEvent
import io.embrace.android.embracesdk.UserSessionListener

/**
 * Bridges a public [UserSessionListener] to the internal one. Delegates [equals]/[hashCode] to the wrapped listener so
 * that a registered listener can be found again for removal despite being wrapped.
 */
internal class UserSessionListenerAdapter(
    private val delegate: UserSessionListener,
) : io.embrace.android.embracesdk.internal.session.UserSessionListener {
    override fun onSessionStateEvent(event: SessionStateEvent) = delegate.onSessionStateEvent(event)
    override fun equals(other: Any?): Boolean = other is UserSessionListenerAdapter && other.delegate == delegate
    override fun hashCode(): Int = delegate.hashCode()
}
