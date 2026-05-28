package io.embrace.android.embracesdk.internal.capture.metadata

interface MetadataService {
    /**
     * Gets the current device's disk usage and space available.
     *
     * @return the device's disk usage statistics
     */
    fun getDiskUsage(): DiskUsage?

    /**
     * Gets the device's time drift relative to its auxiliary clocks if available. The value is sampled during [precomputeValues] so
     * that the underlying IPC stays off any consumer's critical path; this method returns `null` until that work completes, and
     * `null` thereafter if neither auxiliary clock could be read.
     *
     * @return the device's time drift relative to its auxiliary clocks
     */
    fun getClockDrift(): ClockDrift?

    /**
     * Queues in a single thread executor callables to retrieve values in background
     */
    fun precomputeValues()
}
