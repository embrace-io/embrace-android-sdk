package io.embrace.android.embracesdk.internal.gating.v2

import io.embrace.android.embracesdk.internal.gating.SpanSanitizer
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.SessionPayload

internal class EnvelopeSanitizerFacade(
    private val envelope: Envelope<SessionPayload>,
    private val components: Set<String>,
) {

    fun sanitize(): Envelope<SessionPayload> {
        return envelope.copy(
            metadata = EnvelopeMetadataSanitizer(
                envelope.metadata ?: EnvelopeMetadata(),
                components
            ).sanitize(),
            data = envelope.data.copy(
                spans = SpanSanitizer(envelope.data.spans, components).sanitize()
            )
        )
    }
}
