package io.embrace.android.embracesdk.internal.capture.metadata

import io.embrace.android.embracesdk.internal.payload.AppInfo
import io.embrace.android.embracesdk.internal.payload.DeviceInfo

interface MetadataService {

    /**
     * Gets information about the current application being instrumented. This is sent with the
     * following events, as well as all sessions:
     *
     *  * START
     *  * INFO_LOG
     *  * ERROR_LOG
     *  * WARNING_LOG
     *  * CRASH
     *
     * @return the application information
     */
    fun getAppInfo(): AppInfo

    /**
     * Gets information and specifications of the current device. This is sent with the following
     * events, as well as all sessions:
     *
     *  * START
     *  * INFO_LOG
     *  * ERROR_LOG
     *  * WARNING_LOG
     *  * CRASH
     *
     *
     * @return the device information
     */
    fun getDeviceInfo(): DeviceInfo

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
