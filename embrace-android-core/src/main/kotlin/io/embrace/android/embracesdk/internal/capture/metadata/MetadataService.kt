package io.embrace.android.embracesdk.internal.capture.metadata

interface MetadataService {
    /**
     * Gets the current device's disk usage and space available.
     *
     * @return the device's disk usage statistics
     */
    fun getDiskUsage(): DiskUsage?

    /**
     * Gets the current device's time drift relative to its auxiliary clocks if available. This value is read live and does not
     * depend on [precomputeValues].
     *
     * @return the device's time drift relative to its auxiliary clocks
     */
    fun getClockDrift(): ClockDrift?

    /**
     * Queues in a single thread executor callables to retrieve values in background
     */
    fun precomputeValues()
}
