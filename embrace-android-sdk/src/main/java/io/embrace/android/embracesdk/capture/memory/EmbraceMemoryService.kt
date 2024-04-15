package io.embrace.android.embracesdk.capture.memory

import io.embrace.android.embracesdk.injection.DataSourceModule
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Polls for the device's available and used memory.
 *
 * Stores memory warnings when the [ActivityService] detects a memory trim event.
 */
internal class EmbraceMemoryService(
    private val clock: Clock,
    private val dataSourceModuleProvider: Provider<DataSourceModule?>,
) : MemoryService {

    override fun onMemoryWarning() {
        dataSourceModuleProvider()?.memoryWarningDataSource?.dataSource?.onMemoryWarning(clock.now())
    }

    companion object {
        const val MAX_CAPTURED_MEMORY_WARNINGS = 100
    }
}
