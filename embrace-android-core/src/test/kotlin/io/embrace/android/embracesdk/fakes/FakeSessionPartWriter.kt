package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartWriter
import io.embrace.android.embracesdk.internal.state.StateJournalRecord

class FakeSessionPartWriter : SessionPartWriter {

    var metadataChangeCount: Int = 0
    var crashCount: Int = 0

    val completedSpanCalls: MutableList<List<Span>> = mutableListOf()
    val spanSnapshotCalls: MutableList<EmbraceSdkSpan> = mutableListOf()
    val inProgressSessions: MutableList<SessionInfo> = mutableListOf()
    val completedSessions: MutableList<SessionInfo> = mutableListOf()
    val journalRecords: MutableList<StateJournalRecord> = mutableListOf()

    /**
     * Every start, end and journal record in the order the calls were made, as `started:<id>`, `ended:<id>` and
     * `journal:<record type>:<id>`.
     */
    val callLog: MutableList<String> = mutableListOf()

    override fun onSessionPartStarted(timestamp: Long, userSessionId: String, sessionPartId: String) {
        inProgressSessions.add(SessionInfo(timestamp, userSessionId, sessionPartId))
        callLog.add("started:$sessionPartId")
    }

    override fun onSessionPartEnded(sessionPartId: String, crashing: Boolean) {
        val session = inProgressSessions.single { it.sessionPartId == sessionPartId }
        inProgressSessions.remove(session)
        completedSessions.add(session)
        callLog.add("ended:$sessionPartId")
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

    override fun append(record: StateJournalRecord) {
        journalRecords.add(record)
        callLog.add("journal:${record::class.simpleName}:${record.sessionPartId}")
    }

    override fun onCrash() {
        crashCount++
    }

    override fun close() {
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
