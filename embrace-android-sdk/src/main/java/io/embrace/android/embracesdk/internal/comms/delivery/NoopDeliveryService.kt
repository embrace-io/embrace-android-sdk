package io.embrace.android.embracesdk.internal.comms.delivery

import io.embrace.android.embracesdk.internal.ndk.NativeCrashService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NetworkEvent

internal class NoopDeliveryService : DeliveryService {

    override fun sendSession(envelope: Envelope<SessionPayload>, snapshotType: SessionSnapshotType) {
    }

    override fun sendCachedSessions(
        nativeCrashServiceProvider: Provider<NativeCrashService?>,
        sessionIdTracker: SessionIdTracker
    ) {
    }

    override fun sendLog(eventMessage: EventMessage) {
    }

    override fun sendLogs(logEnvelope: Envelope<LogPayload>) {
    }

    override fun saveLogs(logEnvelope: Envelope<LogPayload>) {
    }

    override fun sendNetworkCall(networkEvent: NetworkEvent) {
    }

    override fun sendCrash(crash: EventMessage, processTerminating: Boolean) {
    }

    override fun sendMoment(eventMessage: EventMessage) {
    }
}
