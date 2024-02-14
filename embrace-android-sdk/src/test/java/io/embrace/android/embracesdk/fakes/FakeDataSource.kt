package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.arch.DataSource
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan

internal class FakeDataSource : DataSource<CurrentSessionSpan> {
    var registerCount = 0
    var unregisterCount = 0

    override fun captureData(action: CurrentSessionSpan.() -> Unit) {
        action(FakeCurrentSessionSpan())
    }

    override fun registerListeners() {
        captureData {
            // TODO: interact with span here.
        }
        registerCount++
    }

    override fun unregisterListeners() {
        unregisterCount++
    }
}
