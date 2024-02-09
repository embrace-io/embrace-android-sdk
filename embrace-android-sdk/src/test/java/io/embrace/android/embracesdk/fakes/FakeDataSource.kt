package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.arch.DataSinkMutator
import io.embrace.android.embracesdk.arch.DataSource
import io.embrace.android.embracesdk.arch.SpanEventMapper
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent

internal class FakeDataSource : DataSource<FakeData, Any> {
    var registerCount = 0
    var unregisterCount = 0

    override fun captureData(action: DataSinkMutator<FakeData, Any>) {
    }

    override fun registerListeners() {
        registerCount++
    }

    override fun unregisterListeners() {
        unregisterCount++
    }
}

internal class FakeData : SpanEventMapper {
    override fun toSpanEvent(timestampNanos: Long) = EmbraceSpanEvent(
        "fake_event",
        timestampNanos,
        mapOf("fake" to "fake")
    )
}
