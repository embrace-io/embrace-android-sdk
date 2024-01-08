package io.embrace.android.embracesdk.capture.metadata

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.view.WindowManager
import io.embrace.android.embracesdk.BuildConfig
import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.capture.cpu.CpuInfoDelegate
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.BuildInfo
import io.embrace.android.embracesdk.internal.DeviceArchitecture
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logDebug
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logDeveloper
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logError
import io.embrace.android.embracesdk.payload.AppInfo
import io.embrace.android.embracesdk.payload.DeviceInfo
import io.embrace.android.embracesdk.payload.DiskUsage
import io.embrace.android.embracesdk.prefs.PreferencesService
import io.embrace.android.embracesdk.session.lifecycle.ActivityLifecycleListener
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.utils.eagerLazyLoad
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService

/**
 * Provides information about the state of the device, retrieved from Android system services,
 * which is used as metadata with telemetry submitted to the Embrace API.
 */
internal class EmbraceMetadataService private constructor(
    private val windowManager: WindowManager?,
    private val packageManager: PackageManager,
    private val storageStatsManager: StorageStatsManager?,
    private val activityManager: ActivityManager?,
    private val buildInfo: BuildInfo,
    private val configService: ConfigService,
    private val applicationInfo: ApplicationInfo,
    private val deviceId: Lazy<String>,
    private val packageName: String,
    private val lazyAppVersionName: Lazy<String>,
    private val lazyAppVersionCode: Lazy<String>,
    private val appFramework: AppFramework,
    /**
     * This field is defined during instantiation as by the end of the startup
     */
    private val appUpdated: Lazy<Boolean>,
    private val osUpdated: Lazy<Boolean>,
    private val preferencesService: PreferencesService,
    private val processStateService: ProcessStateService,
    reactNativeBundleId: Lazy<String?>,
    javaScriptPatchNumber: String?,
    reactNativeVersion: String?,
    unityVersion: String?,
    buildGuid: String?,
    unitySdkVersion: String?,
    rnSdkVersion: String?,
    private val metadataRetrieveExecutorService: ExecutorService,
    private val clock: Clock,
    private val embraceCpuInfoDelegate: CpuInfoDelegate,
    private val deviceArchitecture: DeviceArchitecture
) : MetadataService, ActivityLifecycleListener {

    private val statFs = lazy { StatFs(Environment.getDataDirectory().path) }
    private val javaScriptPatchNumber: String?
    private val reactNativeVersion: String?
    private val unityVersion: String?
    private val buildGuid: String?
    private val unitySdkVersion: String?
    private var reactNativeBundleId: Lazy<String?>

    @Volatile
    private var sessionId: String? = null

    @Volatile
    private var diskUsage: DiskUsage? = null

    @Volatile
    private var screenResolution: String? = null

    @Volatile
    private var cpuName: String? = null

    @Volatile
    private var egl: String? = null

    @Volatile
    private var isJailbroken: Boolean? = null
    private var embraceFlutterSdkVersion: String? = null
    private var dartVersion: String? = null
    private var rnSdkVersion: String?

    init {
        if (appFramework == AppFramework.REACT_NATIVE) {
            logDeveloper("EmbraceMetadataService", "Setting RN settings")
            this.reactNativeBundleId = reactNativeBundleId
            this.javaScriptPatchNumber = javaScriptPatchNumber
            this.reactNativeVersion = reactNativeVersion
            this.rnSdkVersion = rnSdkVersion
        } else {
            this.reactNativeBundleId = lazy { buildInfo.buildId }
            this.javaScriptPatchNumber = null
            this.reactNativeVersion = null
            this.rnSdkVersion = null
        }
        if (appFramework == AppFramework.UNITY) {
            logDeveloper("EmbraceMetadataService", "Setting Unity settings")
            this.unityVersion = unityVersion
            this.buildGuid = buildGuid
            this.unitySdkVersion = unitySdkVersion
        } else {
            this.unityVersion = null
            this.buildGuid = null
            this.unitySdkVersion = null
        }
    }

    /**
     * Queues in a single thread executor callables to retrieve values in background
     */
    override fun precomputeValues() {
        logDeveloper(
            "EmbraceMetadataService",
            "Precomputing values asynchronously: Jailbroken/ScreenResolution/DiskUsage"
        )
        asyncRetrieveIsJailbroken()
        asyncRetrieveScreenResolution()
        asyncRetrieveAdditionalDeviceInfo()

        // Always retrieve the DiskUsage last because it can take the longest to run
        asyncRetrieveDiskUsage(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
    }

    private fun asyncRetrieveAdditionalDeviceInfo() {
        if (!configService.autoDataCaptureBehavior.isNdkEnabled()) {
            logDeveloper("EmbraceMetadataService", "NDK not enabled")
            return
        }
        if (!cpuName.isNullOrEmpty() && !egl.isNullOrEmpty()) {
            logDeveloper("EmbraceMetadataService", "Additional device info already exists")
            return
        }
        metadataRetrieveExecutorService.submit {
            logDeveloper("EmbraceMetadataService", "Async retrieve cpuName & egl")
            val storedCpuName = preferencesService.cpuName
            val storedEgl = preferencesService.egl
            if (storedCpuName != null) {
                cpuName = storedCpuName
            } else {
                cpuName = embraceCpuInfoDelegate.getCpuName()
                preferencesService.cpuName = cpuName
                logDeveloper("EmbraceMetadataService", "cpu name computed and stored")
            }
            if (storedEgl != null) {
                egl = storedEgl
            } else {
                egl = embraceCpuInfoDelegate.getElg()
                preferencesService.egl = egl
                logDeveloper("EmbraceMetadataService", "egl computed and stored")
            }
        }
    }

    private fun asyncRetrieveScreenResolution() {
        // if the screenResolution exists in memory, don't try to retrieve it
        if (!screenResolution.isNullOrEmpty()) {
            logDeveloper("EmbraceMetadataService", "Screen resolution already exists")
            return
        }
        metadataRetrieveExecutorService.submit {
            logDeveloper("EmbraceMetadataService", "Async retrieve screen resolution")
            val storedScreenResolution = preferencesService.screenResolution
            // get from shared preferences
            if (storedScreenResolution != null) {
                logDeveloper(
                    "EmbraceMetadataService",
                    "Screen resolution is present, loading from store"
                )
                screenResolution = storedScreenResolution
            } else {
                screenResolution = MetadataUtils.getScreenResolution(
                    windowManager
                )
                preferencesService.screenResolution = screenResolution
                logDeveloper("EmbraceMetadataService", "Screen resolution computed and stored")
            }
        }
    }

    private fun asyncRetrieveIsJailbroken() {
        logDeveloper("EmbraceMetadataService", "Async retrieve Jailbroken")

        // if the isJailbroken property exists in memory, don't try to retrieve it
        if (isJailbroken != null) {
            logDeveloper("EmbraceMetadataService", "Jailbroken already exists")
            return
        }
        metadataRetrieveExecutorService.submit {
            logDeveloper("EmbraceMetadataService", "Async retrieve jailbroken")
            val storedIsJailbroken = preferencesService.jailbroken
            // load value from shared preferences
            if (storedIsJailbroken != null) {
                logDeveloper("EmbraceMetadataService", "Jailbroken is present, loading from store")
                isJailbroken = storedIsJailbroken
            } else {
                isJailbroken = MetadataUtils.isJailbroken()
                preferencesService.jailbroken = isJailbroken
                logDeveloper("EmbraceMetadataService", "Jailbroken processed and stored")
            }
            logDeveloper("EmbraceMetadataService", "Jailbroken: $isJailbroken")
        }
    }

    fun asyncRetrieveDiskUsage(isAndroid26OrAbove: Boolean) {
        metadataRetrieveExecutorService.submit {
            logDeveloper("EmbraceMetadataService", "Async retrieve disk usage")
            val free = MetadataUtils.getInternalStorageFreeCapacity(statFs.value)
            if (isAndroid26OrAbove && configService.autoDataCaptureBehavior.isDiskUsageReportingEnabled()) {
                val deviceDiskAppUsage = MetadataUtils.getDeviceDiskAppUsage(
                    storageStatsManager,
                    packageManager,
                    packageName
                )
                if (deviceDiskAppUsage != null) {
                    logDeveloper("EmbraceMetadataService", "Disk usage is present")
                    diskUsage = DiskUsage(deviceDiskAppUsage, free)
                }
            }
            if (diskUsage == null) {
                diskUsage = DiskUsage(null, free)
            }
            logDeveloper("EmbraceMetadataService", "Device disk free: $free")
        }
    }

    fun getReactNativeBundleId(): String? = reactNativeBundleId.value

    override fun getDeviceId(): String = deviceId.value

    override fun getAppVersionCode(): String = lazyAppVersionCode.value

    override fun getAppVersionName(): String = lazyAppVersionName.value

    override fun getDeviceInfo(): DeviceInfo = getDeviceInfo(true)

    private fun getDeviceInfo(populateAllFields: Boolean): DeviceInfo {
        val storageCapacityBytes = when {
            populateAllFields -> MetadataUtils.getInternalStorageTotalCapacity(statFs.value)
            else -> 0
        }
        return DeviceInfo(
            MetadataUtils.getDeviceManufacturer(),
            MetadataUtils.getModel(),
            deviceArchitecture.architecture,
            isJailbroken(),
            MetadataUtils.getLocale(),
            storageCapacityBytes,
            MetadataUtils.getOperatingSystemType(),
            MetadataUtils.getOperatingSystemVersion(),
            MetadataUtils.getOperatingSystemVersionCode(),
            getScreenResolution(),
            MetadataUtils.getTimezoneId(),
            MetadataUtils.getNumberOfCores(),
            if (populateAllFields) getCpuName() else null,
            if (populateAllFields) getEgl() else null
        )
    }

    override fun getLightweightDeviceInfo(): DeviceInfo = getDeviceInfo(false)

    override fun getAppInfo(): AppInfo = getAppInfo(true)

    @Suppress("CyclomaticComplexMethod", "ComplexMethod")
    private fun getAppInfo(populateAllFields: Boolean): AppInfo {
        var infoPlatformVersion: String? = null
        var hostedSdkVersion: String? = null
        var infoUnityBuildIdNumber: String? = null
        var infoReactNativeBundle: String? = null
        var infoJavaScriptPatchNumber: String? = null
        var infoReactNativeVersion: String? = null
        // applies to Unity builds only.
        if (appFramework == AppFramework.UNITY) {
            infoPlatformVersion = unityVersion ?: preferencesService.unityVersionNumber
            infoUnityBuildIdNumber = buildGuid ?: preferencesService.unityBuildIdNumber
            hostedSdkVersion = unitySdkVersion ?: preferencesService.unitySdkVersionNumber
        }

        // applies to React Native builds only
        if (appFramework == AppFramework.REACT_NATIVE) {
            infoReactNativeBundle = reactNativeBundleId.value
            infoJavaScriptPatchNumber = javaScriptPatchNumber
            infoReactNativeVersion = reactNativeVersion
            hostedSdkVersion = getRnSdkVersion()
        }

        // applies to Flutter builds only
        if (appFramework == AppFramework.FLUTTER) {
            infoPlatformVersion = dartSdkVersion
            hostedSdkVersion = getEmbraceFlutterSdkVersion()
        }
        return AppInfo(
            lazyAppVersionName.value,
            appFramework.value,
            buildInfo.buildId,
            buildInfo.buildType,
            buildInfo.buildFlavor,
            MetadataUtils.appEnvironment(applicationInfo),
            when {
                populateAllFields -> appUpdated.value
                else -> false
            },
            when {
                populateAllFields -> appUpdated.value
                else -> false
            },
            lazyAppVersionCode.value,
            when {
                populateAllFields -> osUpdated.value
                else -> false
            },
            when {
                populateAllFields -> osUpdated.value
                else -> false
            },
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
            infoReactNativeBundle,
            infoJavaScriptPatchNumber,
            infoReactNativeVersion,
            infoPlatformVersion,
            infoUnityBuildIdNumber,
            hostedSdkVersion
        )
    }

    override fun getLightweightAppInfo(): AppInfo = getAppInfo(false)

    private fun getRnSdkVersion(): String? = rnSdkVersion ?: preferencesService.rnSdkVersion

    private val dartSdkVersion: String?
        get() = dartVersion ?: preferencesService.dartSdkVersion

    private fun getEmbraceFlutterSdkVersion(): String? =
        embraceFlutterSdkVersion ?: preferencesService.embraceFlutterSdkVersion

    override fun getAppId(): String {
        return configService.sdkModeBehavior.appId
    }

    override fun isAppUpdated(): Boolean = appUpdated.value

    override fun isOsUpdated(): Boolean = osUpdated.value

    override val activeSessionId: String?
        get() = sessionId

    override fun setActiveSessionId(sessionId: String?, isSession: Boolean) {
        logDeveloper("EmbraceMetadataService", "Active session Id: $sessionId")
        this.sessionId = sessionId

        if (isSession) {
            setSessionIdToProcessStateSummary(this.sessionId)
        }
    }

    override fun removeActiveSessionId(sessionId: String?) {
        if (this.sessionId != null && this.sessionId == sessionId) {
            logDeveloper("EmbraceMetadataService", "Nulling active session Id")
            setActiveSessionId(null, false)
        }
    }

    /**
     * On android 11+, we use ActivityManager#setProcessStateSummary to store sessionId
     * Then, this information will be included in the record of ApplicationExitInfo on the death of the current calling process
     *
     * @param sessionId current session id
     */
    private fun setSessionIdToProcessStateSummary(sessionId: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (sessionId != null) {
                try {
                    activityManager?.setProcessStateSummary(sessionId.toByteArray())
                } catch (e: Throwable) {
                    logError("Couldn't set Process State Summary", e)
                }
            }
        }
    }

    override fun getAppState(): String {
        return if (processStateService.isInBackground) {
            logDeveloper("EmbraceMetadataService", "App state: BACKGROUND")
            "background"
        } else {
            logDeveloper("EmbraceMetadataService", "App state: ACTIVE")
            "active"
        }
    }

    override fun getDiskUsage(): DiskUsage? = diskUsage

    override fun getScreenResolution(): String? = screenResolution

    override fun isJailbroken(): Boolean? = isJailbroken

    override fun getCpuName(): String? = cpuName

    override fun getEgl(): String? = egl

    override fun setReactNativeBundleId(context: Context, jsBundleIdUrl: String?) {
        if (jsBundleIdUrl.isNullOrEmpty()) {
            InternalStaticEmbraceLogger.logError("JavaScript bundle URL must have non-zero length")
            reactNativeBundleId = lazy { buildInfo.buildId }
            return
        }
        val currentUrl = preferencesService.javaScriptBundleURL
        if (currentUrl != null && currentUrl == jsBundleIdUrl) {
            // if the JS bundle ID URL didn't change, use the value from preferences
            InternalStaticEmbraceLogger.logDebug(
                "JavaScript bundle URL already exists and didn't change. " +
                    "Using: " + currentUrl + "."
            )
            reactNativeBundleId = lazy { buildInfo.buildId }
            return
        }

        // if doesn't exists or if is a new JS bundle ID URL, save the new value in preferences
        preferencesService.javaScriptBundleURL = jsBundleIdUrl

        // get the hashed bundle ID file from the bundle ID URL
        reactNativeBundleId = metadataRetrieveExecutorService.eagerLazyLoad(
            Callable {
                computeReactNativeBundleId(
                    context,
                    jsBundleIdUrl,
                    buildInfo.buildId
                )
            }
        )
    }

    override fun setEmbraceFlutterSdkVersion(version: String?) {
        embraceFlutterSdkVersion = version
        preferencesService.embraceFlutterSdkVersion = version
    }

    override fun setRnSdkVersion(version: String?) {
        rnSdkVersion = version
        preferencesService.rnSdkVersion = version
    }

    override fun setDartVersion(version: String?) {
        dartVersion = version
        preferencesService.dartSdkVersion = version
    }

    override fun applicationStartupComplete() {
        val appVersion = getAppVersionName()
        val osVersion = Build.VERSION.RELEASE
        val localDeviceId = getDeviceId()
        val installDate = clock.now()
        logDebug(
            String.format(
                Locale.getDefault(),
                "Setting metadata on preferences service. " +
                    "App version: {%s}, OS version {%s}, device ID: {%s}, install date: {%d}",
                appVersion,
                osVersion,
                localDeviceId,
                installDate
            )
        )
        preferencesService.appVersion = appVersion
        preferencesService.osVersion = osVersion
        preferencesService.deviceIdentifier = localDeviceId
        if (preferencesService.installDate == null) {
            preferencesService.installDate = installDate
        }
        logDeveloper("EmbraceMetadataService", "- Application Startup Complete -")
    }

    companion object {

        /**
         * Creates an instance of the [EmbraceMetadataService] from the device's [Context]
         * for creating Android system services.
         *
         * @param context            the [Context]
         * @param buildInfo          the build information
         * @param appFramework       the framework used by the app
         * @param preferencesService the preferences service
         * @return an instance
         */
        @JvmStatic
        @Suppress("LongParameterList")
        fun ofContext(
            context: Context,
            buildInfo: BuildInfo,
            configService: ConfigService,
            appFramework: AppFramework,
            preferencesService: PreferencesService,
            processStateService: ProcessStateService,
            metadataRetrieveExecutorService: ExecutorService,
            storageStatsManager: StorageStatsManager?,
            windowManager: WindowManager?,
            activityManager: ActivityManager?,
            clock: Clock,
            embraceCpuInfoDelegate: CpuInfoDelegate,
            deviceArchitecture: DeviceArchitecture,
            lazyAppVersionName: Lazy<String>,
            lazyAppVersionCode: Lazy<String>
        ): EmbraceMetadataService {
            val isAppUpdated = lazy {
                val lastKnownAppVersion = preferencesService.appVersion
                val appUpdated = (
                    lastKnownAppVersion != null &&
                        !lastKnownAppVersion.equals(lazyAppVersionName.value, ignoreCase = true)
                    )
                logDeveloper("EmbraceMetadataService", "App updated: $appUpdated")
                appUpdated
            }
            val isOsUpdated = lazy {
                val lastKnownOsVersion = preferencesService.osVersion
                val osUpdated = (
                    lastKnownOsVersion != null &&
                        !lastKnownOsVersion.equals(
                            Build.VERSION.RELEASE,
                            ignoreCase = true
                        )
                    )
                logDeveloper("EmbraceMetadataService", "OS updated: $osUpdated")
                osUpdated
            }
            val deviceIdentifier = lazy(preferencesService::deviceIdentifier)
            var javaScriptPatchNumber: String? = null
            val reactNativeVersion: String? = null
            var rnSdkVersion: String? = null
            val reactNativeBundleId: Lazy<String?>
            if (appFramework == AppFramework.REACT_NATIVE) {
                reactNativeBundleId =
                    metadataRetrieveExecutorService.eagerLazyLoad(
                        Callable {
                            val lastKnownJsBundleUrl = preferencesService.javaScriptBundleURL
                            if (lastKnownJsBundleUrl != null) {
                                computeReactNativeBundleId(
                                    context,
                                    lastKnownJsBundleUrl,
                                    buildInfo.buildId
                                )
                            } else {
                                // If JS bundle ID URL is not found we assume that the App is not using Codepush.
                                // Use JS bundle ID URL as React Native bundle ID.
                                logDeveloper(
                                    "EmbraceMetadataService",
                                    "setting JSBundleUrl as buildId: " + buildInfo.buildId
                                )
                                buildInfo.buildId
                            }
                        }
                    )
                javaScriptPatchNumber = preferencesService.javaScriptPatchNumber
                if (javaScriptPatchNumber != null) {
                    logDeveloper(
                        "EmbraceMetadataService",
                        "Java script patch number: $javaScriptPatchNumber"
                    )
                }
                rnSdkVersion = preferencesService.rnSdkVersion
                if (rnSdkVersion != null) {
                    logDeveloper("EmbraceMetadataService", "RN Embrace SDK version: $rnSdkVersion")
                }
            } else {
                reactNativeBundleId = lazy { buildInfo.buildId }
                logDeveloper("EmbraceMetadataService", "setting default RN as buildId")
            }
            var unityVersion: String? = null
            var buildGuid: String? = null
            var unitySdkVersion: String? = null
            if (appFramework == AppFramework.UNITY) {
                unityVersion = preferencesService.unityVersionNumber
                if (unityVersion != null) {
                    logDeveloper("EmbraceMetadataService", "Unity version: $unityVersion")
                } else {
                    logDeveloper("EmbraceMetadataService", "Unity version is not present")
                }
                buildGuid = preferencesService.unityBuildIdNumber
                if (buildGuid != null) {
                    logDeveloper("EmbraceMetadataService", "Unity build id: $buildGuid")
                } else {
                    logDeveloper("EmbraceMetadataService", "Unity build id number is not present")
                }
                unitySdkVersion = preferencesService.unitySdkVersionNumber
                if (unitySdkVersion != null) {
                    logDeveloper("EmbraceMetadataService", "Unity SDK version: $unitySdkVersion")
                } else {
                    logDeveloper("EmbraceMetadataService", "Unity SDK version is not present")
                }
            }
            return EmbraceMetadataService(
                windowManager,
                context.packageManager,
                storageStatsManager,
                activityManager,
                buildInfo,
                configService,
                context.applicationInfo,
                deviceIdentifier,
                context.packageName,
                lazyAppVersionName,
                lazyAppVersionCode,
                appFramework,
                isAppUpdated,
                isOsUpdated,
                preferencesService,
                processStateService,
                reactNativeBundleId,
                javaScriptPatchNumber,
                reactNativeVersion,
                unityVersion,
                buildGuid,
                unitySdkVersion,
                rnSdkVersion,
                metadataRetrieveExecutorService,
                clock,
                embraceCpuInfoDelegate,
                deviceArchitecture
            )
        }

        private fun getBundleAssetName(bundleUrl: String): String {
            val name = bundleUrl.substring(bundleUrl.indexOf("://") + 3)
            logDeveloper("EmbraceMetadataService", "Asset name: $name")
            return name
        }

        private fun getBundleAsset(context: Context, bundleUrl: String): InputStream? {
            try {
                logDeveloper(
                    "EmbraceMetadataService",
                    "Attempting to read bundle asset: $bundleUrl"
                )
                return context.assets.open(getBundleAssetName(bundleUrl))
            } catch (e: Exception) {
                InternalStaticEmbraceLogger.logError("Failed to retrieve RN bundle file from assets.", e)
            }
            return null
        }

        private fun getCustomBundleStream(bundleUrl: String): InputStream? {
            try {
                logDeveloper(
                    "EmbraceMetadataService",
                    "Attempting to load bundle from custom path: $bundleUrl"
                )
                return FileInputStream(bundleUrl)
            } catch (e: NullPointerException) {
                InternalStaticEmbraceLogger.logError("Failed to retrieve the custom RN bundle file.", e)
            } catch (e: FileNotFoundException) {
                InternalStaticEmbraceLogger.logError("Failed to retrieve the custom RN bundle file.", e)
            }
            return null
        }

        internal fun computeReactNativeBundleId(
            context: Context,
            bundleUrl: String,
            defaultBundleId: String?
        ): String? {
            val bundleStream: InputStream?
            // checks if the bundle url is an asset
            if (bundleUrl.contains("assets")) {
                // looks for the bundle file in assets
                bundleStream = getBundleAsset(context, bundleUrl)
            } else {
                // looks for the bundle file from the custom path
                bundleStream = getCustomBundleStream(bundleUrl)
                logDeveloper(
                    "EmbraceMetadataService",
                    "Loaded bundle file from custom path: $bundleStream"
                )
            }
            if (bundleStream == null) {
                logDeveloper(
                    "EmbraceMetadataService",
                    "Setting default RN bundleId: $defaultBundleId"
                )
                return defaultBundleId
            }
            try {
                bundleStream.use { inputStream ->
                    ByteArrayOutputStream().use { buffer ->
                        var read: Int
                        // The hash size for the MD5 algorithm is 128 bits - 16 bytes.
                        val data = ByteArray(16)
                        while (inputStream.read(data, 0, data.size).also { read = it } != -1) {
                            buffer.write(data, 0, read)
                        }
                        return hashBundleToMd5(buffer.toByteArray())
                    }
                }
            } catch (e: Exception) {
                logError("Failed to compute the RN bundle file.", e)
            }
            logDeveloper("EmbraceMetadataService", "Setting default RN bundleId: $defaultBundleId")
            // if the hashing of the JS bundle URL fails, returns the default bundle ID
            return defaultBundleId
        }

        fun isEmulator(): Boolean = MetadataUtils.isEmulator()

        private fun hashBundleToMd5(bundle: ByteArray): String {
            val hashBundle: String
            val md = MessageDigest.getInstance("MD5")
            val bundleHashed = md.digest(bundle)
            val sb = StringBuilder()
            for (b in bundleHashed) {
                sb.append(String.format(Locale.getDefault(), "%02x", b.toInt() and 0xff))
            }
            hashBundle = sb.toString().toUpperCase(Locale.getDefault())
            logDeveloper("EmbraceMetadataService", "Setting RN bundleId: $hashBundle")
            return hashBundle
        }
    }
}
