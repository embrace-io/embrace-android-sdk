package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NetworkEvent
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.id.SessionIdTracker
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType

internal interface DeliveryService {
    fun sendSession(sessionMessage: SessionMessage, snapshotType: SessionSnapshotType)
    fun sendCachedSessions(ndkService: NdkService?, sessionIdTracker: SessionIdTracker)
    fun sendLog(eventMessage: EventMessage)
    fun sendLogs(logEnvelope: Envelope<LogPayload>)
    fun sendNetworkCall(networkEvent: NetworkEvent)
    fun sendCrash(crash: EventMessage, processTerminating: Boolean)
    fun sendMoment(eventMessage: EventMessage)
}
