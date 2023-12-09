package io.embrace.android.embracesdk.capture.monitor

import io.embrace.android.embracesdk.anr.detection.LivenessCheckScheduler
import io.embrace.android.embracesdk.anr.detection.ResponsivenessMonitor

internal class EmbraceResponsivenessMonitorService(
    private val livenessCheckScheduler: LivenessCheckScheduler
) : ResponsivenessMonitorService {
    override fun getCapturedData(): List<ResponsivenessMonitor.Snapshot> = livenessCheckScheduler.responsivenessMonitorSnapshots()

    override fun cleanCollections() {
        livenessCheckScheduler.resetResponsivenessMonitors()
    }
}
