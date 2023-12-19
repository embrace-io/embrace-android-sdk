package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.comms.api.SerializationAction
import io.embrace.android.embracesdk.comms.delivery.DeliveryCacheManager
import io.embrace.android.embracesdk.comms.delivery.PendingApiCalls
import io.embrace.android.embracesdk.payload.BackgroundActivityMessage
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.SessionSnapshotType

internal class FakeDeliveryCacheManager : DeliveryCacheManager {

    override fun saveSession(sessionMessage: SessionMessage, snapshotType: SessionSnapshotType) {
        TODO("Not yet implemented")
    }

    override fun loadSession(sessionId: String): SessionMessage? {
        TODO("Not yet implemented")
    }

    override fun loadSessionAsAction(sessionId: String): SerializationAction? {
        TODO("Not yet implemented")
    }

    override fun deleteSession(sessionId: String) {
        TODO("Not yet implemented")
    }

    override fun getAllCachedSessionIds(): List<String> {
        TODO("Not yet implemented")
    }

    override fun saveBackgroundActivity(backgroundActivityMessage: BackgroundActivityMessage): ByteArray? {
        TODO("Not yet implemented")
    }

    override fun loadBackgroundActivity(backgroundActivityId: String): ByteArray? {
        TODO("Not yet implemented")
    }

    override fun saveCrash(crash: EventMessage) {
        TODO("Not yet implemented")
    }

    override fun loadCrash(): EventMessage? {
        TODO("Not yet implemented")
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
}
