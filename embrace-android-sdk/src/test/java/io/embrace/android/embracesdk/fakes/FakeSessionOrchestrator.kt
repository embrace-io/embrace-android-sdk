package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.session.orchestrator.SessionOrchestrator

internal class FakeSessionOrchestrator : SessionOrchestrator {

    var manualEndCount = 0

    override fun endSessionWithManual(clearUserInfo: Boolean) {
        manualEndCount++
    }
}
