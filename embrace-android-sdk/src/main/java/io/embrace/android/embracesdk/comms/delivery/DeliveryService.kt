package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.BackgroundActivityMessage
import io.embrace.android.embracesdk.payload.BlobMessage
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NetworkEvent
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.SessionSnapshotType

internal interface DeliveryService {
    fun sendSession(sessionMessage: SessionMessage, snapshotType: SessionSnapshotType)
    fun sendCachedSessions(isNdkEnabled: Boolean, ndkService: NdkService, currentSession: String?)
    fun saveBackgroundActivity(backgroundActivityMessage: BackgroundActivityMessage)
    fun sendBackgroundActivity(backgroundActivityMessage: BackgroundActivityMessage)
    fun sendBackgroundActivities()
    fun sendLog(eventMessage: EventMessage)
    fun sendNetworkCall(networkEvent: NetworkEvent)
    fun sendCrash(crash: EventMessage)
    fun sendAEIBlob(blobMessage: BlobMessage)
    fun sendMoment(eventMessage: EventMessage)
    fun sendEventAndWait(eventMessage: EventMessage)
}
