package io.embrace.android.embracesdk.internal.instrumentation.anr

import io.embrace.android.embracesdk.internal.envelope.session.OtelPayloadMapper
import io.embrace.android.embracesdk.internal.payload.Span

/**
 * Handles logic for features that are not fully integrated into the OTel pipeline.
 */
class OtelPayloadMapperImpl(
    private val anrOtelMapper: AnrOtelMapper?,
) : OtelPayloadMapper {

    override fun snapshotSpans(): List<Span> {
        return anrOtelMapper?.snapshot() ?: emptyList()
    }

    override fun record() {
        anrOtelMapper?.record()
    }
}
