package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.session.MemoryCleanerListener
import io.embrace.android.embracesdk.internal.session.MemoryCleanerService

class FakeMemoryCleanerService : MemoryCleanerService {

    var callCount: Int = 0
    val listeners: MutableList<MemoryCleanerListener> = mutableListOf()

    override fun addListener(listener: MemoryCleanerListener) {
        listeners.add(listener)
    }

    override fun cleanServicesCollections() {
        callCount++
    }
}
