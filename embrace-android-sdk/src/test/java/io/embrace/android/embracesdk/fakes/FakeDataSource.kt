package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.arch.DataSinkMutator
import io.embrace.android.embracesdk.arch.DataSource

internal class FakeDataSource : DataSource {
    var registerCount = 0
    var unregisterCount = 0

    override fun captureData(action: DataSinkMutator) {
    }

    override fun registerListeners() {
        registerCount++
    }

    override fun unregisterListeners() {
        unregisterCount++
    }
}
