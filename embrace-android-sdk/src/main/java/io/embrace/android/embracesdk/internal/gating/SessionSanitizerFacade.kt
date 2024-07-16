package io.embrace.android.embracesdk.internal.gating

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.SessionPayload

internal class SessionSanitizerFacade(
    private val envelope: Envelope<SessionPayload>,
    private val components: Set<String>
) {

    fun getSanitizedMessage(): Envelope<SessionPayload> {
        val sanitizedSpans = SpanSanitizer(envelope.data.spans, components).sanitize()

        return envelope.copy(
            metadata = sanitizeMetadata(components),
            resource = sanitizeResource(components),
            data = envelope.data.copy(spans = sanitizedSpans)
        )
    }

    private fun sanitizeMetadata(enabledComponents: Set<String>): EnvelopeMetadata {
        if (envelope.metadata == null) {
            return EnvelopeMetadata()
        }
        if (!shouldSendUserPersonas(enabledComponents)) {
            return envelope.metadata.copy(personas = null)
        }
        return envelope.metadata
    }

    private fun sanitizeResource(enabledComponents: Set<String>): EnvelopeResource {
        if (envelope.resource == null) {
            return EnvelopeResource()
        }
        if (!shouldSendCurrentDiskUsage(enabledComponents)) {
            return envelope.resource.copy(diskTotalCapacity = null)
        }
        return envelope.resource
    }

    private fun shouldSendCurrentDiskUsage(enabledComponents: Set<String>) =
        enabledComponents.contains(SessionGatingKeys.PERFORMANCE_CURRENT_DISK_USAGE)

    private fun shouldSendUserPersonas(enabledComponents: Set<String>) =
        enabledComponents.contains(SessionGatingKeys.USER_PERSONAS)
}
