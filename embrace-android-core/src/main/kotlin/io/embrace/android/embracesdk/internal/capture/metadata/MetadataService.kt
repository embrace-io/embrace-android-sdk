package io.embrace.android.embracesdk.internal.capture.metadata

import android.content.Context
import io.embrace.android.embracesdk.internal.payload.AppInfo
import io.embrace.android.embracesdk.internal.payload.DeviceInfo
import io.embrace.android.embracesdk.internal.payload.DiskUsage

public interface MetadataService {

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
    public fun getAppInfo(): AppInfo

    /**
     * Same as [.getAppInfo] but does not search for information (in preferences, for example) that is
     * not already loaded in memory in the service.
     */
    public fun getLightweightAppInfo(): AppInfo

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
    public fun getDeviceInfo(): DeviceInfo

    /**
     * Same as [.getDeviceInfo] but does not get storage information from the file system.
     */
    public fun getLightweightDeviceInfo(): DeviceInfo

    /**
     * Gets the current device's disk usage and space available.
     *
     * @return the device's disk usage statistics
     */
    public fun getDiskUsage(): DiskUsage?

    /**
     * Sets React Native Bundle ID from a custom JavaScript Bundle URL.
     * @param context the context
     * @param jsBundleUrl the JavaScript bundle URL
     * @param forceUpdate if the bundle was updated and we need to recompute the bundleId
     */
    public fun setReactNativeBundleId(context: Context, jsBundleUrl: String?, forceUpdate: Boolean? = null)

    public fun getReactNativeBundleId(): String?

    /**
     * Queues in a single thread executor callables to retrieve values in background
     */
    public fun precomputeValues()
}
