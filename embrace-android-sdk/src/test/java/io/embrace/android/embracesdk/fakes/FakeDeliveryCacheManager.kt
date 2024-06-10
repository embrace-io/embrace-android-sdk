package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.comms.delivery.CachedSession
import io.embrace.android.embracesdk.comms.delivery.DeliveryCacheManager
import io.embrace.android.embracesdk.comms.delivery.PendingApiCalls
import io.embrace.android.embracesdk.getSessionId
import io.embrace.android.embracesdk.internal.compression.ConditionalGzipOutputStream
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.utils.SerializationAction
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType

internal class FakeDeliveryCacheManager : DeliveryCacheManager {

    val saveSessionRequests = mutableListOf<Pair<SessionMessage, SessionSnapshotType>>()
    val saveBgActivityRequests = mutableListOf<SessionMessage>()
    val saveCrashRequests = mutableListOf<EventMessage>()

    private val cachedSessions = mutableListOf<SessionMessage>()
    private val serializer = EmbraceSerializer()

    override fun saveSession(sessionMessage: SessionMessage, snapshotType: SessionSnapshotType) {
        saveSessionRequests.add(Pair(sessionMessage, snapshotType))
    }

    override fun loadSessionAsAction(sessionId: String): SerializationAction? {
        val message = cachedSessions.singleOrNull { it.getSessionId() == sessionId } ?: return null
        return { stream ->
            ConditionalGzipOutputStream(stream).use {
                serializer.toJson(message, SessionMessage::class.java, it)
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

    override fun transformSession(sessionId: String, transformer: (SessionMessage) -> SessionMessage) {
        TODO("Not yet implemented")
    }

    fun addCachedSessions(vararg sessionMessages: SessionMessage) {
        cachedSessions.addAll(sessionMessages)
    }
}
