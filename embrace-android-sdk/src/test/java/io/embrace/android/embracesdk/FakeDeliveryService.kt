package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.BackgroundActivityMessage
import io.embrace.android.embracesdk.payload.BlobMessage
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NetworkEvent
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.SessionSnapshotType

/**
 * A [DeliveryService] that records the last parameters used to invoke each method, and for the ones that need it, count the number of
 * invocations. Please add additional tracking functionality as tests require them.
 */
internal class FakeDeliveryService : DeliveryService {
    var lastSentNetworkCall: NetworkEvent? = null
    var lastSentCrash: EventMessage? = null
    var lastSentEvent: EventMessage? = null
    val lastSentLogs: MutableList<EventMessage> = mutableListOf()
    var sendBackgroundActivitiesInvokedCount: Int = 0
    var lastSentBackgroundActivity: BackgroundActivityMessage? = null
    var saveBackgroundActivityInvokedCount: Int = 0
    var lastSavedBackgroundActivity: BackgroundActivityMessage? = null
    var lastEventSentAsync: EventMessage? = null
    var eventSentAsyncInvokedCount: Int = 0
    var lastSavedCrash: EventMessage? = null
    var lastSentCachedSession: String? = null
    var lastSavedSession: SessionMessage? = null
    var lastSnapshotType: SessionSnapshotType? = null
    val lastSentSessions: MutableList<Pair<SessionMessage, SessionSnapshotType>> = mutableListOf()
    var blobMessages: MutableList<BlobMessage> = mutableListOf()

    override fun sendSession(sessionMessage: SessionMessage, snapshotType: SessionSnapshotType) {
        if (snapshotType != SessionSnapshotType.PERIODIC_CACHE) {
            lastSentSessions.add(sessionMessage to snapshotType)
        }
        lastSavedSession = sessionMessage
        lastSnapshotType = snapshotType
    }

    override fun sendCachedSessions(isNdkEnabled: Boolean, ndkService: NdkService, currentSession: String?) {
        lastSentCachedSession = currentSession
    }

    override fun sendEventAsync(eventMessage: EventMessage) {
        eventSentAsyncInvokedCount++
        lastEventSentAsync = eventMessage
    }

    override fun saveBackgroundActivity(backgroundActivityMessage: BackgroundActivityMessage) {
        saveBackgroundActivityInvokedCount++
        lastSavedBackgroundActivity = backgroundActivityMessage
    }

    override fun sendBackgroundActivity(backgroundActivityMessage: BackgroundActivityMessage) {
        lastSentBackgroundActivity = backgroundActivityMessage
    }

    override fun sendBackgroundActivities() {
        sendBackgroundActivitiesInvokedCount++
    }

    override fun sendLog(eventMessage: EventMessage) {
        lastSentLogs.add(eventMessage)
    }

    override fun sendNetworkCall(networkEvent: NetworkEvent) {
        lastSentNetworkCall = networkEvent
    }

    override fun sendEventAndWait(eventMessage: EventMessage) {
        lastSentEvent = eventMessage
    }

    override fun sendCrash(crash: EventMessage) {
        lastSavedCrash = crash
        lastSentCrash = crash
    }

    override fun sendAEIBlob(blobMessage: BlobMessage) {
        blobMessages.add(blobMessage)
    }
}
