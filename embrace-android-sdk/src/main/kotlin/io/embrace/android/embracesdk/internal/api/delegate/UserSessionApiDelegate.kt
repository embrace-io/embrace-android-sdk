package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.SessionStateEvent
import io.embrace.android.embracesdk.UserSessionListener
import io.embrace.android.embracesdk.internal.api.UserSessionApi
import io.embrace.android.embracesdk.internal.capture.session.UserSessionPropertiesService
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.internal.session.UserSessionListener as InternalUserSessionListener

internal class UserSessionApiDelegate(
    private val userSessionPropertiesService: UserSessionPropertiesService,
    private val sessionOrchestrator: SessionOrchestrator,
) : UserSessionApi {

    /**
     * Adds a property to the current session.
     */
    override fun addUserSessionProperty(key: String, value: String, scope: PropertyScope): Boolean {
        val internalScope = when (scope) {
            PropertyScope.USER_SESSION -> io.embrace.android.embracesdk.internal.capture.session.PropertyScope.USER_SESSION
            PropertyScope.PROCESS -> io.embrace.android.embracesdk.internal.capture.session.PropertyScope.PROCESS
            PropertyScope.PERMANENT -> io.embrace.android.embracesdk.internal.capture.session.PropertyScope.PERMANENT
        }
        return userSessionPropertiesService.addProperty(key, value, internalScope)
    }

    /**
     * Removes a property from the current session.
     */
    override fun removeUserSessionProperty(key: String): Boolean {
        return userSessionPropertiesService.removeProperty(key)
    }

    /**
     * Ends the current session and starts a new one.
     */
    override fun endUserSession() {
        sessionOrchestrator.endSessionWithManual()
    }

    override fun addUserSessionListener(listener: UserSessionListener) {
        sessionOrchestrator.addUserSessionListener(ListenerWrapper(listener))
    }

    override fun removeUserSessionListener(listener: UserSessionListener) {
        sessionOrchestrator.removeUserSessionListener(ListenerWrapper(listener))
    }

    /**
     * Bridges a public [UserSessionListener] to the internal one. Delegates [equals]/[hashCode] to the wrapped listener so that a
     * listener registered via [addUserSessionListener] can be found again (for dedup and removal) despite being wrapped.
     */
    private class ListenerWrapper(
        private val delegate: UserSessionListener,
    ) : InternalUserSessionListener {
        override fun onSessionStateEvent(event: SessionStateEvent) = delegate.onSessionStateEvent(event)
        override fun equals(other: Any?): Boolean = other is ListenerWrapper && other.delegate == delegate
        override fun hashCode(): Int = delegate.hashCode()
    }
}
