package io.embrace.android.embracesdk.gating

import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.payload.SessionMessage

internal class SessionSanitizerFacade(
    private val sessionMessage: SessionMessage,
    private val components: Set<String>
) {

    fun getSanitizedMessage(): SessionMessage {
        val sanitizedSession = SessionSanitizer(sessionMessage.session, components).sanitize()
        val sanitizedPerformanceInfo = PerformanceInfoSanitizer(sessionMessage.performanceInfo, components).sanitize()
        val sanitizedSpans = SpanSanitizer(sessionMessage.spans, components).sanitize()

        return sessionMessage.copy(
            session = sanitizedSession,
            performanceInfo = sanitizedPerformanceInfo,
            spans = sanitizedSpans,
            metadata = sanitizeMetadata(components)
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

    private fun shouldSendUserPersonas(enabledComponents: Set<String>) =
        enabledComponents.contains(SessionGatingKeys.USER_PERSONAS)
}
