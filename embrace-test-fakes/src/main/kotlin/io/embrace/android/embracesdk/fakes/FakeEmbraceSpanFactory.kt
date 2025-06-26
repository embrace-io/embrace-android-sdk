package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanFactory
import io.embrace.android.embracesdk.internal.otel.spans.OtelSpanCreator
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan

class FakeEmbraceSpanFactory(
    private val spanToReturn: EmbraceSdkSpan = FakeEmbraceSdkSpan.stopped()
) : EmbraceSpanFactory {
    override fun create(
        name: String,
        type: EmbType,
        internal: Boolean,
        private: Boolean,
        parent: EmbraceSpan?,
        autoTerminationMode: AutoTerminationMode,
    ): EmbraceSdkSpan = spanToReturn

    override fun create(
        otelSpanCreator: OtelSpanCreator,
        autoTerminationMode: AutoTerminationMode
    ): EmbraceSdkSpan = spanToReturn
}
