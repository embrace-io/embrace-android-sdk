package io.embrace.android.embracesdk.capture.monitor

import io.embrace.android.embracesdk.anr.detection.ResponsivenessMonitor

internal class NoOpResponsivenessMonitorService : ResponsivenessMonitorService {
    override fun getCapturedData(): List<ResponsivenessMonitor.Snapshot> = emptyList()

    override fun cleanCollections() {}
}
