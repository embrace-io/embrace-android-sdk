package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator

public class FakeSessionOrchestrator : SessionOrchestrator {

    public var crashId: String? = null
    public var manualEndCount: Int = 0
    public var stateChangeCount: Int = 0

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
