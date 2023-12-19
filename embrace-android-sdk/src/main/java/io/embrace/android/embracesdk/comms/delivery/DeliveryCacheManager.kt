package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.comms.api.SerializationAction
import io.embrace.android.embracesdk.payload.BackgroundActivityMessage
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.SessionSnapshotType

internal interface DeliveryCacheManager {
    fun saveSession(sessionMessage: SessionMessage, snapshotType: SessionSnapshotType)
    fun loadSession(sessionId: String): SessionMessage?
    fun loadSessionAsAction(sessionId: String): SerializationAction?
    fun deleteSession(sessionId: String)
    fun getAllCachedSessionIds(): List<String>
    fun saveBackgroundActivity(backgroundActivityMessage: BackgroundActivityMessage): ByteArray?
    fun loadBackgroundActivity(backgroundActivityId: String): ByteArray?
    fun saveCrash(crash: EventMessage)
    fun loadCrash(): EventMessage?
    fun deleteCrash()
    fun savePayload(action: SerializationAction): String
    fun loadPayload(name: String): ByteArray?
    fun loadPayloadAsAction(name: String): SerializationAction
    fun deletePayload(name: String)
    fun savePendingApiCalls(pendingApiCalls: PendingApiCalls)
    fun loadPendingApiCalls(): PendingApiCalls
}
