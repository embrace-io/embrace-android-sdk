package io.embrace.android.embracesdk.internal.comms.delivery

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.ndk.NativeCrashService
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NetworkEvent
import io.embrace.android.embracesdk.session.id.SessionIdTracker
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType

internal interface DeliveryService {
    fun sendSession(envelope: Envelope<SessionPayload>, snapshotType: SessionSnapshotType)
    fun sendCachedSessions(
        nativeCrashServiceProvider: Provider<NativeCrashService?>,
        sessionIdTracker: SessionIdTracker
    )
    fun sendLog(eventMessage: EventMessage)
    fun sendLogs(logEnvelope: Envelope<LogPayload>)
    fun saveLogs(logEnvelope: Envelope<LogPayload>)
    fun sendNetworkCall(networkEvent: NetworkEvent)
    fun sendCrash(crash: EventMessage, processTerminating: Boolean)
    fun sendMoment(eventMessage: EventMessage)
}
