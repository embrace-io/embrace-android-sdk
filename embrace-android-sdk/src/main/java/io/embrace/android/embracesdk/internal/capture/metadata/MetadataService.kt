package io.embrace.android.embracesdk.internal.capture.metadata

import android.content.Context
import io.embrace.android.embracesdk.internal.payload.AppInfo
import io.embrace.android.embracesdk.internal.payload.DeviceInfo
import io.embrace.android.embracesdk.internal.payload.DiskUsage

internal interface MetadataService {

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
     * Same as [.getAppInfo] but does not search for information (in preferences, for example) that is
     * not already loaded in memory in the service.
     */
    fun getLightweightAppInfo(): AppInfo

    /**
     * Gets the app ID which is defined as part of the configuration.
     *
     * @return the app ID.
     */
    fun getAppId(): String?

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
     * Same as [.getDeviceInfo] but does not get storage information from the file system.
     */
    fun getLightweightDeviceInfo(): DeviceInfo

    /**
     * Gets the current device's disk usage and space available.
     *
     * @return the device's disk usage statistics
     */
    fun getDiskUsage(): DiskUsage?

    /**
     * Returns 'foreground' if the application is in the foreground, or 'background' if the app is in
     * the background.
     *
     * @return the current state of the app
     */
    fun getAppState(): String?

    /**
     * Sets React Native Bundle ID from a custom JavaScript Bundle URL.
     * @param context the context
     * @param jsBundleUrl the JavaScript bundle URL
     * @param forceUpdate if the bundle was updated and we need to recompute the bundleId
     */
    fun setReactNativeBundleId(context: Context, jsBundleUrl: String?, forceUpdate: Boolean? = null)

    fun getReactNativeBundleId(): String?

    /**
     * Queues in a single thread executor callables to retrieve values in background
     */
    fun precomputeValues()
}
