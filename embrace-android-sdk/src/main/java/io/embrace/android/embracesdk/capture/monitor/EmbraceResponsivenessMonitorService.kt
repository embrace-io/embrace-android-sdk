package io.embrace.android.embracesdk.capture.monitor

import io.embrace.android.embracesdk.anr.detection.LivenessCheckScheduler
import io.embrace.android.embracesdk.payload.ResponsivenessSnapshot

internal class EmbraceResponsivenessMonitorService(
    private val livenessCheckScheduler: LivenessCheckScheduler
) : ResponsivenessMonitorService {
    override fun getCapturedData(): List<ResponsivenessSnapshot> = livenessCheckScheduler.responsivenessMonitorSnapshots()

    override fun cleanCollections() {
        livenessCheckScheduler.resetResponsivenessMonitors()
    }
}
