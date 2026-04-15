package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.session.UserSessionListener
import io.embrace.android.embracesdk.internal.session.UserSessionMetadata
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator

class FakeSessionOrchestrator : SessionOrchestrator {

    var crashId: String? = null
    var manualEndCount: Int = 0
    var stateChangeCount: Int = 0
    var currentSession: UserSessionMetadata? = null
    val userSessionListeners = mutableListOf<UserSessionListener>()

    override fun endSessionWithManual() {
        manualEndCount++
    }

    override fun handleCrash(crashId: String) {
        this.crashId = crashId
    }

    override fun onSessionDataUpdate() {
        stateChangeCount++
    }

    override fun onBackground() {
    }

    override fun onForeground() {
    }

    override fun currentUserSession(): UserSessionMetadata? = currentSession

    override fun addUserSessionListener(listener: UserSessionListener) {
        userSessionListeners.add(listener)
    }
}
