package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.getSessionId
import io.embrace.android.embracesdk.internal.comms.delivery.CachedSession
import io.embrace.android.embracesdk.internal.comms.delivery.DeliveryCacheManager
import io.embrace.android.embracesdk.internal.comms.delivery.PendingApiCalls
import io.embrace.android.embracesdk.internal.compression.ConditionalGzipOutputStream
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.utils.SerializationAction
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType

internal class FakeDeliveryCacheManager : DeliveryCacheManager {

    val saveSessionRequests = mutableListOf<Pair<Envelope<SessionPayload>, SessionSnapshotType>>()
    val saveBgActivityRequests = mutableListOf<Envelope<SessionPayload>>()
    val saveCrashRequests = mutableListOf<EventMessage>()

    private val cachedSessions = mutableListOf<Envelope<SessionPayload>>()
    private val serializer = EmbraceSerializer()

    override fun saveSession(envelope: Envelope<SessionPayload>, snapshotType: SessionSnapshotType) {
        saveSessionRequests.add(Pair(envelope, snapshotType))
    }

    override fun loadSessionAsAction(sessionId: String): SerializationAction? {
        val message = cachedSessions.singleOrNull { it.getSessionId() == sessionId } ?: return null
        return { stream ->
            ConditionalGzipOutputStream(stream).use {
                serializer.toJson(message, Envelope.sessionEnvelopeType, it)
            }
        }
    }

    override fun deleteSession(sessionId: String) {
        TODO("Not yet implemented")
    }

    override fun getAllCachedSessionIds(): List<CachedSession> {
        return cachedSessions.map { CachedSession.create(it.getSessionId(), 0, false) }
    }

    override fun saveCrash(crash: EventMessage) {
        saveCrashRequests.add(crash)
    }

    override fun loadCrash(): EventMessage? {
        return null
    }

    override fun deleteCrash() {
        TODO("Not yet implemented")
    }

    override fun deletePayload(name: String) {
        TODO("Not yet implemented")
    }

    override fun savePendingApiCalls(pendingApiCalls: PendingApiCalls, sync: Boolean) {
        TODO("Not yet implemented")
    }

    override fun loadPendingApiCalls(): PendingApiCalls {
        TODO("Not yet implemented")
    }

    override fun savePayload(action: SerializationAction): String {
        TODO("Not yet implemented")
    }

    override fun loadPayloadAsAction(name: String): SerializationAction {
        TODO("Not yet implemented")
    }

    override fun transformSession(
        sessionId: String,
        transformer: (Envelope<SessionPayload>) -> Envelope<SessionPayload>
    ) {
        TODO("Not yet implemented")
    }

    fun addCachedSessions(vararg envelopes: Envelope<SessionPayload>) {
        cachedSessions.addAll(envelopes)
    }
}
