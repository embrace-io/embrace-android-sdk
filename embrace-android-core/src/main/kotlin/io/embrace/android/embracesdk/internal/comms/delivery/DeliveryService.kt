package io.embrace.android.embracesdk.internal.comms.delivery

import io.embrace.android.embracesdk.internal.ndk.NativeCrashService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EventMessage
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.NetworkEvent
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.internal.utils.Provider

public interface DeliveryService {
    public fun sendSession(envelope: Envelope<SessionPayload>, snapshotType: SessionSnapshotType)
    public fun sendCachedSessions(
        nativeCrashServiceProvider: Provider<NativeCrashService?>,
        sessionIdTracker: SessionIdTracker
    )
    public fun sendLog(eventMessage: EventMessage)
    public fun sendLogs(logEnvelope: Envelope<LogPayload>)
    public fun saveLogs(logEnvelope: Envelope<LogPayload>)
    public fun sendNetworkCall(networkEvent: NetworkEvent)
    public fun sendCrash(crash: EventMessage, processTerminating: Boolean)
    public fun sendMoment(eventMessage: EventMessage)
}
