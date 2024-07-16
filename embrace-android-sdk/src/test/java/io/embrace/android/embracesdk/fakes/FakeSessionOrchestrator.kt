package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator

internal class FakeSessionOrchestrator : SessionOrchestrator {

    var crashId: String? = null
    var manualEndCount = 0
    var stateChangeCount = 0

    override fun endSessionWithManual(clearUserInfo: Boolean) {
        manualEndCount++
    }

    override fun endSessionWithCrash(crashId: String) {
        this.crashId = crashId
    }

    override fun reportBackgroundActivityStateChange() {
        stateChangeCount++
    }
}
