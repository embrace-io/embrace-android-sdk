package io.embrace.android.embracesdk.internal.gating

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.SessionPayload

internal class SessionSanitizerFacade(
    private val envelope: Envelope<SessionPayload>,
    private val components: Set<String>,
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
        val metadata = envelope.metadata ?: return EnvelopeMetadata()
        if (!shouldSendUserPersonas(enabledComponents)) {
            return metadata.copy(personas = null)
        }
        return metadata
    }

    private fun sanitizeResource(enabledComponents: Set<String>): EnvelopeResource {
        val resource = envelope.resource ?: return EnvelopeResource()
        if (!shouldSendCurrentDiskUsage(enabledComponents)) {
            return resource.copy(diskTotalCapacity = null)
        }
        return resource
    }

    private fun shouldSendCurrentDiskUsage(enabledComponents: Set<String>) =
        enabledComponents.contains(SessionGatingKeys.PERFORMANCE_CURRENT_DISK_USAGE)

    private fun shouldSendUserPersonas(enabledComponents: Set<String>) =
        enabledComponents.contains(SessionGatingKeys.USER_PERSONAS)
}
