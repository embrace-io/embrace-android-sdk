package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.logs.LogOrchestrator

public class FakeLogOrchestrator : LogOrchestrator {

    public var flushCalled: Boolean = false

    override fun flush(saveOnly: Boolean) {
        flushCalled = true
    }
}
