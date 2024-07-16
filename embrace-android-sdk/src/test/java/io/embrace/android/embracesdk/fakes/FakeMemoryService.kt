package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.capture.memory.MemoryService

internal class FakeMemoryService : MemoryService {

    var callCount = 0

    override fun onMemoryWarning() {
        callCount++
    }
}
