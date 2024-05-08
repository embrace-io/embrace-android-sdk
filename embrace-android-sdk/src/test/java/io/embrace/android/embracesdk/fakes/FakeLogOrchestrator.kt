package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.logs.LogOrchestrator

internal class FakeLogOrchestrator : LogOrchestrator {

    var flushCalled = false

    override fun flush(saveOnly: Boolean) {
        flushCalled = true
    }
}
