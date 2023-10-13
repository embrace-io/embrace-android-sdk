package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.AppExitInfoData
import io.embrace.android.embracesdk.payload.BackgroundActivityMessage
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NetworkEvent
import io.embrace.android.embracesdk.payload.SessionMessage

internal enum class SessionMessageState { START, END, END_WITH_CRASH }

internal interface DeliveryService : DeliveryServiceNetwork {
    fun saveSession(sessionMessage: SessionMessage)
    fun sendSession(sessionMessage: SessionMessage, state: SessionMessageState)
    fun sendCachedSessions(isNdkEnabled: Boolean, ndkService: NdkService, currentSession: String?)
    fun saveCrash(crash: EventMessage)
    fun sendEventAsync(eventMessage: EventMessage)
    fun saveBackgroundActivity(backgroundActivityMessage: BackgroundActivityMessage)
    fun sendBackgroundActivity(backgroundActivityMessage: BackgroundActivityMessage)
    fun sendBackgroundActivities()
}

internal interface DeliveryServiceNetwork {
    fun sendLogs(eventMessage: EventMessage)
    fun sendNetworkCall(networkEvent: NetworkEvent)
    fun sendEvent(eventMessage: EventMessage)
    fun sendEventAndWait(eventMessage: EventMessage)
    fun sendCrash(crash: EventMessage)
    fun sendAEIBlob(appExitInfoData: List<AppExitInfoData>)
}
