package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.comms.api.SerializationAction
import io.embrace.android.embracesdk.comms.delivery.DeliveryCacheManager
import io.embrace.android.embracesdk.comms.delivery.PendingApiCalls
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.payload.BackgroundActivityMessage
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.SessionSnapshotType

internal class FakeDeliveryCacheManager : DeliveryCacheManager {

    val saveSessionRequests = mutableListOf<Pair<SessionMessage, SessionSnapshotType>>()
    val saveBgActivityRequests = mutableListOf<BackgroundActivityMessage>()
    val saveCrashRequests = mutableListOf<EventMessage>()

    private val cachedSessions = mutableListOf<SessionMessage>()
    private val serializer = EmbraceSerializer()

    override fun saveSession(sessionMessage: SessionMessage, snapshotType: SessionSnapshotType) {
        saveSessionRequests.add(Pair(sessionMessage, snapshotType))
    }

    override fun loadSession(sessionId: String): SessionMessage? {
        TODO("Not yet implemented")
    }

    override fun loadSessionAsAction(sessionId: String): SerializationAction? {
        val message = cachedSessions.singleOrNull { it.session.sessionId == sessionId } ?: return null
        return {
            serializer.toJson(message, SessionMessage::class.java, it)
        }
    }

    override fun deleteSession(sessionId: String) {
        TODO("Not yet implemented")
    }

    override fun getAllCachedSessionIds(): List<String> {
        return cachedSessions.map { it.session.sessionId }
    }

    override fun saveBackgroundActivity(backgroundActivityMessage: BackgroundActivityMessage): SerializationAction? {
        saveBgActivityRequests.add(backgroundActivityMessage)
        return {
            serializer.toJson(backgroundActivityMessage, BackgroundActivityMessage::class.java, it)
        }
    }

    override fun loadBackgroundActivity(backgroundActivityId: String): SerializationAction? {
        val message = saveBgActivityRequests.singleOrNull {
            it.backgroundActivity.sessionId == backgroundActivityId
        } ?: return null
        return {
            serializer.toJson(message, BackgroundActivityMessage::class.java, it)
        }
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

    override fun loadPayload(name: String): ByteArray? {
        TODO("Not yet implemented")
    }

    override fun deletePayload(name: String) {
        TODO("Not yet implemented")
    }

    override fun savePendingApiCalls(pendingApiCalls: PendingApiCalls) {
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

    fun addCachedSessions(vararg sessionMessages: SessionMessage) {
        cachedSessions.addAll(sessionMessages)
    }
}
