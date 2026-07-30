package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.SessionStateEvent
import io.embrace.android.embracesdk.UserSessionListener
import io.embrace.android.embracesdk.internal.api.UserSessionApi
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.embraceImplInject
import io.embrace.android.embracesdk.internal.session.UserSessionListener as InternalUserSessionListener

internal class UserSessionApiDelegate(
    bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker,
) : UserSessionApi {

    private val userSessionPropertiesService by embraceImplInject(sdkCallChecker) {
        bootstrapper.essentialServiceModule.userSessionPropertiesService
    }
    private val sessionOrchestrator by embraceImplInject(sdkCallChecker) {
        bootstrapper.userSessionOrchestrationModule.sessionOrchestrator
    }

    /**
     * Adds a property to the current session.
     */
    override fun addUserSessionProperty(key: String, value: String, scope: PropertyScope): Boolean {
        if (sdkCallChecker.check("add_session_property")) {
            val internalScope = when (scope) {
                PropertyScope.USER_SESSION -> io.embrace.android.embracesdk.internal.capture.session.PropertyScope.USER_SESSION
                PropertyScope.PROCESS -> io.embrace.android.embracesdk.internal.capture.session.PropertyScope.PROCESS
                PropertyScope.PERMANENT -> io.embrace.android.embracesdk.internal.capture.session.PropertyScope.PERMANENT
            }
            return userSessionPropertiesService?.addProperty(key, value, internalScope) ?: false
        }
        return false
    }

    /**
     * Removes a property from the current session.
     */
    override fun removeUserSessionProperty(key: String): Boolean {
        if (sdkCallChecker.check("remove_session_property")) {
            return userSessionPropertiesService?.removeProperty(key) ?: false
        }
        return false
    }

    /**
     * Ends the current session and starts a new one.
     */
    override fun endUserSession() {
        if (sdkCallChecker.check("end_session")) {
            sessionOrchestrator?.endSessionWithManual()
        }
    }

    override fun addUserSessionListener(listener: UserSessionListener) {
        if (sdkCallChecker.check("add_user_session_listener")) {
            sessionOrchestrator?.addUserSessionListener(ListenerWrapper(listener))
        }
    }

    override fun removeUserSessionListener(listener: UserSessionListener) {
        if (sdkCallChecker.check("remove_user_session_listener")) {
            sessionOrchestrator?.removeUserSessionListener(ListenerWrapper(listener))
        }
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
