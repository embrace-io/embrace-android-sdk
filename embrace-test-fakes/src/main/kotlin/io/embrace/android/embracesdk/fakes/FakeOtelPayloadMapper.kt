package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.envelope.session.OtelPayloadMapper
import io.embrace.android.embracesdk.internal.payload.Span

class FakeOtelPayloadMapper : OtelPayloadMapper {
    override fun snapshotSpans(): List<Span> = emptyList()
    override fun record() { }
}
