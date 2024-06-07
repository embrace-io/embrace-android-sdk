package io.embrace.android.embracesdk.gating

import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.payload.SessionMessage

internal class SessionSanitizerFacade(
    private val sessionMessage: SessionMessage,
    private val components: Set<String>
) {

    fun getSanitizedMessage(): SessionMessage {
        val sanitizedSession = SessionSanitizer(sessionMessage.session, components).sanitize()
        val sanitizedSpans = SpanSanitizer(sessionMessage.data?.spans, components).sanitize()

        return sessionMessage.copy(
            session = sanitizedSession,
            metadata = sanitizeMetadata(components),
            resource = sanitizeResource(components),
            data = sessionMessage.data?.copy(spans = sanitizedSpans)
        )
    }

    private fun sanitizeMetadata(enabledComponents: Set<String>): EnvelopeMetadata {
        if (sessionMessage.metadata == null) {
            return EnvelopeMetadata()
        }
        if (!shouldSendUserPersonas(enabledComponents)) {
            return sessionMessage.metadata.copy(personas = null)
        }
        return sessionMessage.metadata
    }

    private fun sanitizeResource(enabledComponents: Set<String>): EnvelopeResource {
        if (sessionMessage.resource == null) {
            return EnvelopeResource()
        }
        if (!shouldSendCurrentDiskUsage(enabledComponents)) {
            return sessionMessage.resource.copy(diskTotalCapacity = null)
        }
        return sessionMessage.resource
    }

    private fun shouldSendCurrentDiskUsage(enabledComponents: Set<String>) =
        enabledComponents.contains(SessionGatingKeys.PERFORMANCE_CURRENT_DISK_USAGE)

    private fun shouldSendUserPersonas(enabledComponents: Set<String>) =
        enabledComponents.contains(SessionGatingKeys.USER_PERSONAS)
}
