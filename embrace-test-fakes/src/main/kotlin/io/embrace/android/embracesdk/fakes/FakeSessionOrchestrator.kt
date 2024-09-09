package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator

class FakeSessionOrchestrator : SessionOrchestrator {

    var crashId: String? = null
    var manualEndCount: Int = 0
    var stateChangeCount: Int = 0

    override fun endSessionWithManual(clearUserInfo: Boolean) {
        manualEndCount++
    }

    override fun handleCrash(crashId: String) {
        this.crashId = crashId
    }

    override fun reportBackgroundActivityStateChange() {
        stateChangeCount++
    }
}
