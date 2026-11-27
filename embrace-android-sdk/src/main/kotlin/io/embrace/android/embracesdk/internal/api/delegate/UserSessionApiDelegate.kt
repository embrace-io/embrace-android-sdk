package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.UserSessionListener
import io.embrace.android.embracesdk.internal.api.UserSessionApi
import io.embrace.android.embracesdk.internal.capture.session.UserSessionPropertiesService
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator

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

    override fun removeUserSessionListener(listener: UserSessionListener) {
        sessionOrchestrator.removeUserSessionListener(UserSessionListenerAdapter(listener))
    }
}
