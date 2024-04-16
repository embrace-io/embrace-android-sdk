package io.embrace.android.embracesdk.gating

import io.embrace.android.embracesdk.payload.SessionMessage

internal class SessionSanitizerFacade(
    private val sessionMessage: SessionMessage,
    private val components: Set<String>
) {

    fun getSanitizedMessage(): SessionMessage {
        val sanitizedSession = SessionSanitizer(sessionMessage.session, components).sanitize()
        val sanitizedUserInfo = UserInfoSanitizer(sessionMessage.userInfo, components).sanitize()
        val sanitizedPerformanceInfo = PerformanceInfoSanitizer(sessionMessage.performanceInfo, components).sanitize()
        val sanitizedSpans = SpanSanitizer(sessionMessage.spans, components).sanitize()

        return sessionMessage.copy(
            session = sanitizedSession,
            userInfo = sanitizedUserInfo,
            performanceInfo = sanitizedPerformanceInfo,
            spans = sanitizedSpans
        )
    }
}
