package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType.JVM_CRASH
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType.NORMAL_END
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker

class V1PayloadStore(
    private val worker: BackgroundWorker,
    private val deliveryService: DeliveryService
) : PayloadStore {

    override fun storeSessionPayload(envelope: Envelope<SessionPayload>, transitionType: TransitionType) {
        val type = when (transitionType) {
            TransitionType.CRASH -> JVM_CRASH
            else -> NORMAL_END
        }
        deliveryService.sendSession(envelope, type)
    }

    override fun storeLogPayload(envelope: Envelope<LogPayload>, attemptImmediateRequest: Boolean) {
        if (attemptImmediateRequest) {
            deliveryService.sendLogs(envelope)
        } else {
            worker.submit {
                deliveryService.saveLogs(envelope)
            }
        }
    }

    override fun cacheSessionSnapshot(envelope: Envelope<SessionPayload>) {
        deliveryService.sendSession(envelope, SessionSnapshotType.PERIODIC_CACHE)
    }

    override fun handleCrash(crashId: String) {
        // ignored - v1 couples this concept to storage
    }
}
