package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.capture.memory.MemoryService
import io.embrace.android.embracesdk.payload.MemoryWarning

internal class FakeMemoryService : FakeDataCaptureService<MemoryWarning>(), MemoryService {

    override fun onMemoryWarning() {
        TODO("Not yet implemented")
    }
}
