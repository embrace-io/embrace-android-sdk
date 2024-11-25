package io.embrace.android.embracesdk.internal.capture.metadata

interface MetadataService {
    /**
     * Gets the current device's disk usage and space available.
     *
     * @return the device's disk usage statistics
     */
    fun getDiskUsage(): DiskUsage?

    /**
     * Queues in a single thread executor callables to retrieve values in background
     */
    fun precomputeValues()
}
