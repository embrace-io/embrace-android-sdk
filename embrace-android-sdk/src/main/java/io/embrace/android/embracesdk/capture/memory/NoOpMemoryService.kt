package io.embrace.android.embracesdk.capture.memory

import io.embrace.android.embracesdk.payload.MemoryWarning

internal class NoOpMemoryService : MemoryService {

    override fun onMemoryWarning() {
    }

    override fun cleanCollections() {
    }

    override fun getCapturedData(): List<MemoryWarning> {
        return emptyList()
    }
}
