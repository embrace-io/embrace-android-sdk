package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.session.MemoryCleanerListener

public class FakeMemoryCleanerListener : MemoryCleanerListener {

    public val callCount: Int get() = counter
    private var counter = 0

    override fun cleanCollections() {
        counter += 1
    }
}
