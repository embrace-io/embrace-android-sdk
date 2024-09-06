package io.embrace.android.embracesdk.internal.comms.delivery

import io.embrace.android.embracesdk.internal.injection.SerializationAction
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType

interface DeliveryCacheManager {
    fun saveSession(envelope: Envelope<SessionPayload>, snapshotType: SessionSnapshotType)
    fun transformSession(sessionId: String, transformer: Envelope<SessionPayload>.() -> Envelope<SessionPayload>)
    fun loadSessionAsAction(sessionId: String): SerializationAction?
    fun deleteSession(sessionId: String)
    fun getAllCachedSessionIds(): List<CachedSession>
    fun savePayload(action: SerializationAction, sync: Boolean = false): String
    fun loadPayloadAsAction(name: String): SerializationAction
    fun deletePayload(name: String)
    fun savePendingApiCallQueue(queue: PendingApiCallQueue, sync: Boolean = false)
    fun loadPendingApiCallQueue(): PendingApiCallQueue
}
