package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanFactory
import io.embrace.android.embracesdk.internal.otel.spans.OtelSpanStartArgs

class FakeEmbraceSpanFactory(
    private val spanToReturn: EmbraceSdkSpan = FakeEmbraceSdkSpan.stopped(),
) : EmbraceSpanFactory {
    override fun create(otelSpanStartArgs: OtelSpanStartArgs): EmbraceSdkSpan = spanToReturn
}
