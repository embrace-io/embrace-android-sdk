package io.embrace.android.embracesdk.fakes

import android.content.Context
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.payload.AppInfo
import io.embrace.android.embracesdk.payload.DeviceInfo
import io.embrace.android.embracesdk.payload.DiskUsage

/**
 * Fake implementation of [MetadataService] that represents an Android device. A [UnsupportedOperationException] will be thrown
 * if you attempt set info about Flutter/Unity/ReactNative on this fake, which is decided for an Android device.
 */
internal class FakeAndroidMetadataService(sessionId: String? = null) : MetadataService {
    companion object {
        private val androidAppInfo = AppInfo(
            appVersion = "1.0.0",
            appFramework = Embrace.AppFramework.NATIVE.value,
            buildId = "100",
            buildType = "release",
            buildFlavor = "oem",
            environment = "prod",
            bundleVersion = "5ac7fe",
            sdkSimpleVersion = "5.10.0",
            sdkVersion = "5.11.0",
            buildGuid = "5092abc"
        )
        private val androidDeviceInfo = DeviceInfo()
        private val diskUsage = DiskUsage(
            appDiskUsage = 10000000L,
            deviceDiskFree = 500000000L
        )
        private const val screenResolution = "1080x720"
        private const val fakeAppVersion: String = "1"
        private const val fakeAppVersionName: String = "1.0.0"
        private const val APP_STATE_FOREGROUND = "foreground"
        private const val APP_STATE_BACKGROUND = "background"
        private const val cpuName = "fakeCpu"
        private const val egl = "fakeEgl"
    }

    var appUpdated = false
    var osUpdated = false
    var fakeAppId: String = "o0o0o"
    var fakeDeviceId: String = "07D85B44E4E245F4A30E559BFC0D07FF"
    var fakeReactNativeBundleId: String? = "fakeReactNativeBundleId"
    var fakeFlutterSdkVersion: String? = "fakeFlutterSdkVersion"
    var fakeDartVersion: String? = "fakeDartVersion"
    var fakeRnSdkVersion: String? = "fakeRnSdkVersion"

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

    override fun getAppId(): String = fakeAppId

    override fun getDeviceInfo(): DeviceInfo = androidDeviceInfo

    override fun getLightweightDeviceInfo(): DeviceInfo = androidDeviceInfo

    override fun getDiskUsage(): DiskUsage = diskUsage

    override fun getScreenResolution(): String = screenResolution

    override fun isJailbroken(): Boolean = false

    override fun getDeviceId(): String = fakeDeviceId

    override fun getAppVersionCode(): String = fakeAppVersion

    override fun getAppVersionName(): String = fakeAppVersionName

    override fun isAppUpdated(): Boolean = appUpdated

    override fun isOsUpdated(): Boolean = osUpdated

    override val activeSessionId: String?
        get() = appSessionId

    override fun setActiveSessionId(sessionId: String?) {
        appSessionId = sessionId
    }

    override fun removeActiveSessionId(sessionId: String?) {
        if (appSessionId == sessionId) {
            appSessionId = null
        }
    }

    override fun getAppState(): String = appState

    override fun setReactNativeBundleId(context: Context, jsBundleIdUrl: String?) {
        fakeReactNativeBundleId = jsBundleIdUrl
    }

    override fun setEmbraceFlutterSdkVersion(version: String?) {
        fakeFlutterSdkVersion = version
    }

    override fun setRnSdkVersion(version: String?) {
        fakeRnSdkVersion = version
    }

    override fun setDartVersion(version: String?) {
        fakeDartVersion = version
    }

    override fun precomputeValues() {}
    override fun getCpuName(): String? = cpuName

    override fun getEgl(): String? = egl
}
