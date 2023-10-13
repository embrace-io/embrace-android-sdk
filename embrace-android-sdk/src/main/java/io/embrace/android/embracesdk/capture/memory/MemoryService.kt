package io.embrace.android.embracesdk.capture.memory

import io.embrace.android.embracesdk.arch.DataCaptureService
import io.embrace.android.embracesdk.payload.MemoryWarning

/**
 * Provides access to information about the state of the device's memory usage.
 */
internal interface MemoryService : DataCaptureService<List<MemoryWarning>?> {

    /**
     * Called when the memory is 'trimmed' by Android and records a
     * [low memory warning](https://developer.android.com/reference/android/content/ComponentCallbacks2).
     *
     * Android trims memory when it is running low.
     */
    fun onMemoryWarning()
}
