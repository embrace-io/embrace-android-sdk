package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.ndk.NativeCrashService
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NetworkEvent
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.id.SessionIdTracker
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType

internal class NoopDeliveryService : DeliveryService {

    override fun sendSession(sessionMessage: SessionMessage, snapshotType: SessionSnapshotType) {
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
