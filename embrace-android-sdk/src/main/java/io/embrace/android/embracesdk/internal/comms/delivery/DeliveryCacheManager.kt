package io.embrace.android.embracesdk.internal.comms.delivery

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.internal.utils.SerializationAction
import io.embrace.android.embracesdk.payload.EventMessage

internal interface DeliveryCacheManager {
    fun saveSession(envelope: Envelope<SessionPayload>, snapshotType: SessionSnapshotType)
    fun transformSession(sessionId: String, transformer: Envelope<SessionPayload>.() -> Envelope<SessionPayload>)
    fun loadSessionAsAction(sessionId: String): SerializationAction?
    fun deleteSession(sessionId: String)
    fun getAllCachedSessionIds(): List<CachedSession>
    fun saveCrash(crash: EventMessage)
    fun loadCrash(): EventMessage?
    fun deleteCrash()
    fun savePayload(action: SerializationAction): String
    fun loadPayloadAsAction(name: String): SerializationAction
    fun deletePayload(name: String)
    fun savePendingApiCalls(pendingApiCalls: PendingApiCalls, sync: Boolean = false)
    fun loadPendingApiCalls(): PendingApiCalls
}
