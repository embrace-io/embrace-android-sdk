package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.session.UserSessionMetadata
import io.embrace.android.embracesdk.internal.session.UserSessionOrchestrator

class FakeUserSessionOrchestrator : UserSessionOrchestrator {

    var newSessionPartCount: Int = 0
    var manualEndCount: Int = 0
    var currentSession: UserSessionMetadata? = null

    override fun onNewSessionPart() {
        newSessionPartCount++
    }

    override fun onManualEnd() {
        manualEndCount++
    }

    override fun currentUserSession(): UserSessionMetadata? = currentSession
}
