package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.internal.utils.SerializationAction
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType

internal interface DeliveryCacheManager {
    fun saveSession(sessionMessage: SessionMessage, snapshotType: SessionSnapshotType)
    fun transformSession(sessionId: String, transformer: (SessionMessage) -> SessionMessage)
    fun loadSessionAsAction(sessionId: String): SerializationAction?
    fun deleteSession(sessionId: String)
    fun getAllCachedSessionIds(): List<CachedSession>
    fun saveCrash(crash: EventMessage)
    fun loadCrash(): EventMessage?
    fun deleteCrash()
    fun savePayload(action: SerializationAction): String
    fun loadPayloadAsAction(name: String): SerializationAction
    fun deletePayload(name: String)
    fun savePendingApiCalls(pendingApiCalls: PendingApiCalls)
    fun loadPendingApiCalls(): PendingApiCalls
}
