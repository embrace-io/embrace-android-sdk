package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.BackgroundActivityMessage
import io.embrace.android.embracesdk.payload.BlobMessage
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NetworkEvent
import io.embrace.android.embracesdk.payload.SessionMessage

internal enum class SessionMessageState { START, END, END_WITH_CRASH }

internal interface DeliveryService {
    fun saveSessionPeriodicCache(sessionMessage: SessionMessage)
    fun saveSessionOnCrash(sessionMessage: SessionMessage)
    fun saveSession(sessionMessage: SessionMessage)
    fun sendSession(sessionMessage: SessionMessage, state: SessionMessageState)
    fun sendCachedSessions(isNdkEnabled: Boolean, ndkService: NdkService, currentSession: String?)
    fun saveCrash(crash: EventMessage)
    fun saveBackgroundActivity(backgroundActivityMessage: BackgroundActivityMessage)
    fun sendBackgroundActivity(backgroundActivityMessage: BackgroundActivityMessage)
    fun sendBackgroundActivities()
    fun sendLog(eventMessage: EventMessage)
    fun sendNetworkCall(networkEvent: NetworkEvent)
    fun sendCrash(crash: EventMessage)
    fun sendAEIBlob(blobMessage: BlobMessage)
    fun sendEventAsync(eventMessage: EventMessage)
    fun sendEventAndWait(eventMessage: EventMessage)
}
