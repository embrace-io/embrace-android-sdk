package io.embrace.android.embracesdk.internal.comms.delivery

import io.embrace.android.embracesdk.internal.ndk.NativeCrashService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EventMessage
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.internal.utils.Provider

class NoopDeliveryService : DeliveryService {

    override fun sendSession(envelope: Envelope<SessionPayload>, snapshotType: SessionSnapshotType) {
    }

    override fun sendCachedSessions(
        nativeCrashServiceProvider: Provider<NativeCrashService?>,
        sessionIdTracker: SessionIdTracker
    ) {
    }

    override fun sendLogs(logEnvelope: Envelope<LogPayload>) {
    }

    override fun saveLogs(logEnvelope: Envelope<LogPayload>) {
    }

    override fun sendMoment(eventMessage: EventMessage) {
    }
}
