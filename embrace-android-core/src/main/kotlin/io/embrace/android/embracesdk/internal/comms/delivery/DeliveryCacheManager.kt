package io.embrace.android.embracesdk.internal.comms.delivery

import io.embrace.android.embracesdk.internal.injection.SerializationAction
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EventMessage
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType

public interface DeliveryCacheManager {
    public fun saveSession(envelope: Envelope<SessionPayload>, snapshotType: SessionSnapshotType)
    public fun transformSession(sessionId: String, transformer: Envelope<SessionPayload>.() -> Envelope<SessionPayload>)
    public fun loadSessionAsAction(sessionId: String): SerializationAction?
    public fun deleteSession(sessionId: String)
    public fun getAllCachedSessionIds(): List<CachedSession>
    public fun saveCrash(crash: EventMessage)
    public fun loadCrash(): EventMessage?
    public fun deleteCrash()
    public fun savePayload(action: SerializationAction): String
    public fun loadPayloadAsAction(name: String): SerializationAction
    public fun deletePayload(name: String)
    public fun savePendingApiCallQueue(queue: PendingApiCallQueue, sync: Boolean = false)
    public fun loadPendingApiCallQueue(): PendingApiCallQueue
}
