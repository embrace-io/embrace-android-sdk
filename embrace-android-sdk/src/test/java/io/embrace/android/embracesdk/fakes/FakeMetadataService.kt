package io.embrace.android.embracesdk.fakes

import android.content.Context
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.payload.AppInfo
import io.embrace.android.embracesdk.internal.payload.DeviceInfo
import io.embrace.android.embracesdk.internal.payload.DiskUsage

/**
 * Fake implementation of [MetadataService] that represents an Android device. A [UnsupportedOperationException] will be thrown
 * if you attempt set info about Flutter/Unity/ReactNative on this fake, which is decided for an Android device.
 */
internal class FakeMetadataService(sessionId: String? = null) : MetadataService {
    companion object {
        private val androidAppInfo = AppInfo(
            appVersion = "1.0.0",
            buildId = "100",
            buildType = "release",
            buildFlavor = "oem",
            environment = "prod",
            bundleVersion = "5ac7fe",
            sdkSimpleVersion = "53",
            sdkVersion = "5.11.0",
            buildGuid = "5092abc",
            reactNativeBundleId = "fakeReactNativeBundleId",
            reactNativeVersion = "fakeRnSdkVersion",
            javaScriptPatchNumber = "js",
            hostedPlatformVersion = "19",
            hostedSdkVersion = "1.2.0"
        )
        private val androidDeviceInfo = DeviceInfo(
            manufacturer = "Samsung",
            model = "SM-G950U",
            architecture = "arm64-v8a",
            jailbroken = false,
            internalStorageTotalCapacity = 10000000L,
            operatingSystemType = "Android",
            operatingSystemVersion = "8.0.0",
            operatingSystemVersionCode = 26,
            screenResolution = "1080x720",
            cores = 8
        )
        private val diskUsage = DiskUsage(
            appDiskUsage = 10000000L,
            deviceDiskFree = 500000000L
        )
        private const val APP_STATE_FOREGROUND = "foreground"
        private const val APP_STATE_BACKGROUND = "background"
    }

    var fakeUnityVersion = "fakeUnityVersion"
    var fakeUnityBuildIdNumber = "fakeUnityBuildIdNumber"
    var fakeUnitySdkVersion = "fakeUnitySdkVersion"
    var appUpdated = false
    var osUpdated = false
    var fakeAppId: String = "o0o0o"
    var fakeDeviceId: String = "07D85B44E4E245F4A30E559BFC0D07FF"
    var fakeReactNativeBundleId: String? = "fakeReactNativeBundleId"
    var forceUpdate: Boolean? = null
    var fakeFlutterSdkVersion: String? = "fakeFlutterSdkVersion"
    var fakeDartVersion: String? = "fakeDartVersion"
    var fakeReactNativeVersion: String? = "fakeReactNativeVersion"
    var fakeJavaScriptPatchNumber: String? = "fakeJavaScriptPatchNumber"
    var fakeRnSdkVersion: String? = "fakeRnSdkVersion"
    val fakePackageName: String = "com.embrace.fake"

    private lateinit var appState: String
    private var appSessionId: String? = null

    init {
        setAppForeground()
        appSessionId = sessionId
    }

    fun setAppForeground() {
        appState = APP_STATE_FOREGROUND
    }

    fun setAppId(id: String) {
        fakeAppId = id
    }

    fun setAppBackground() {
        appState = APP_STATE_BACKGROUND
    }

    override fun getAppInfo(): AppInfo = androidAppInfo

    override fun getLightweightAppInfo(): AppInfo = androidAppInfo

    override fun getDeviceInfo(): DeviceInfo = androidDeviceInfo

    override fun getLightweightDeviceInfo(): DeviceInfo = androidDeviceInfo

    override fun getDiskUsage(): DiskUsage = diskUsage

    override fun setReactNativeBundleId(context: Context, jsBundleUrl: String?, forceUpdate: Boolean?) {
        fakeReactNativeBundleId = jsBundleUrl
        this.forceUpdate = forceUpdate
    }

    override fun getReactNativeBundleId(): String? {
        return fakeReactNativeBundleId
    }

    override fun precomputeValues() {}
}
