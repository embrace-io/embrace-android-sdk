package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.session.orchestrator.SessionOrchestrator

internal class FakeSessionOrchestrator : SessionOrchestrator {

    var crashId: String? = null
    var manualEndCount = 0

    override fun endSessionWithManual(clearUserInfo: Boolean) {
        manualEndCount++
    }

    override fun endSessionWithCrash(crashId: String) {
        this.crashId = crashId
    }

    override fun reportBackgroundActivityStateChange() {
    }
}
