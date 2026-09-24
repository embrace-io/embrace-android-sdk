package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartWriter

class FakeSessionPartWriter : SessionPartWriter {

    var metadataChangeCount: Int = 0
    var crashCount: Int = 0

    val completedSpanCalls: MutableList<List<Span>> = mutableListOf()
    val spanSnapshotCalls: MutableList<EmbraceSdkSpan> = mutableListOf()
    val inProgressSessions: MutableList<SessionInfo> = mutableListOf()
    val completedSessions: MutableList<SessionInfo> = mutableListOf()

    override fun onSessionPartStarted(timestamp: Long, userSessionId: String, sessionPartId: String) {
        inProgressSessions.add(SessionInfo(timestamp, userSessionId, sessionPartId))
    }

    override fun onSessionPartEnded(sessionPartId: String, crashing: Boolean) {
        val session = inProgressSessions.single { it.sessionPartId == sessionPartId }
        inProgressSessions.remove(session)
        completedSessions.add(session)
    }

    override fun onMetadataChanged() {
        metadataChangeCount++
    }

    override fun onSpanCompleted(spans: List<Span>): Boolean {
        return completedSpanCalls.add(spans)
    }

    override fun onSpanSnapshotChanged(span: EmbraceSdkSpan) {
        spanSnapshotCalls.add(span)
    }

    override fun onCrash() {
        crashCount++
    }

    data class SessionInfo(
        val timestamp: Long,
        val userSessionId: String,
        val sessionPartId: String,
    )

    data class EndCall(
        val sessionPartId: String,
    )
}
