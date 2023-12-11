package io.embrace.android.embracesdk.capture.monitor

import io.embrace.android.embracesdk.payload.ResponsivenessSnapshot

internal class NoOpResponsivenessMonitorService : ResponsivenessMonitorService {
    override fun getCapturedData(): List<ResponsivenessSnapshot> = emptyList()

    override fun cleanCollections() {}
}
