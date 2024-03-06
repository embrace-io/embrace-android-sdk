package io.embrace.android.embracesdk.capture.metadata

import android.app.usage.StorageStatsManager
import android.content.Context
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
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logDebug
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logDeveloper
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logError
import io.embrace.android.embracesdk.payload.AppInfo
import io.embrace.android.embracesdk.payload.DeviceInfo
import io.embrace.android.embracesdk.payload.DiskUsage
import io.embrace.android.embracesdk.prefs.PreferencesService
import io.embrace.android.embracesdk.session.lifecycle.ActivityLifecycleListener
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.worker.BackgroundWorker
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Future

/**
 * Provides information about the state of the device, retrieved from Android system services,
 * which is used as metadata with telemetry submitted to the Embrace API.
 */
internal class EmbraceMetadataService private constructor(
    private val windowManager: WindowManager?,
    private val packageManager: PackageManager,
    private val storageStatsManager: StorageStatsManager?,
    private val buildInfo: BuildInfo,
    private val configService: ConfigService,
    private val environment: AppEnvironment.Environment,
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
    reactNativeBundleId: Future<String?>,
    private val hostedSdkVersionInfo: HostedSdkVersionInfo,
    private val metadataBackgroundWorker: BackgroundWorker,
    private val clock: Clock,
    private val embraceCpuInfoDelegate: CpuInfoDelegate,
    private val deviceArchitecture: DeviceArchitecture
) : MetadataService, ActivityLifecycleListener {

    private val statFs = lazy { StatFs(Environment.getDataDirectory().path) }
    private var reactNativeBundleId: Future<String?>

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
    init {
        if (appFramework == AppFramework.REACT_NATIVE) {
            logDeveloper("EmbraceMetadataService", "Setting RN settings")
            this.reactNativeBundleId = reactNativeBundleId
        } else {
            this.reactNativeBundleId = metadataBackgroundWorker.submit<String?> { buildInfo.buildId }
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
        if (!cpuName.isNullOrEmpty() && !egl.isNullOrEmpty()) {
            logDeveloper("EmbraceMetadataService", "Additional device info already exists")
            return
        }
        metadataBackgroundWorker.submit {
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
        metadataBackgroundWorker.submit {
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
        metadataBackgroundWorker.submit {
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
        metadataBackgroundWorker.submit {
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

    /**
     * Return the bundle Id if it was already calculated in background or null if it's not ready yet.
     * This way, we avoid blocking the main thread to wait for the value.
     */
    fun getReactNativeBundleId(): String? =
        if (appFramework == AppFramework.REACT_NATIVE && reactNativeBundleId.isDone) {
            reactNativeBundleId.get()
        } else {
            null
        }

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
        return AppInfo(
            lazyAppVersionName.value,
            appFramework.value,
            buildInfo.buildId,
            buildInfo.buildType,
            buildInfo.buildFlavor,
            environment.value,
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
            getReactNativeBundleId(),
            hostedSdkVersionInfo.javaScriptPatchNumber,
            hostedSdkVersionInfo.hostedPlatformVersion,
            hostedSdkVersionInfo.hostedPlatformVersion,
            hostedSdkVersionInfo.unityBuildIdNumber,
            hostedSdkVersionInfo.hostedSdkVersion
        )
    }

    override fun getLightweightAppInfo(): AppInfo = getAppInfo(false)

    override fun getAppId(): String {
        return configService.sdkModeBehavior.appId
    }

    override fun isAppUpdated(): Boolean = appUpdated.value

    override fun isOsUpdated(): Boolean = osUpdated.value

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

    override fun getAppFramework() = appFramework
    override fun getPackageName() = packageName

    override fun setReactNativeBundleId(context: Context, jsBundleUrl: String?, forceUpdate: Boolean?) {
        val currentUrl = preferencesService.javaScriptBundleURL

        if (currentUrl != jsBundleUrl || forceUpdate == true) {
            // It`s a new JS bundle URL, save the new value in preferences.
            preferencesService.javaScriptBundleURL = jsBundleUrl

            // Calculate the bundle ID for the new bundle URL
            reactNativeBundleId = metadataBackgroundWorker.submit<String?> {
                val bundleId = computeReactNativeBundleId(
                    context,
                    jsBundleUrl,
                    buildInfo.buildId
                )
                if (forceUpdate != null) {
                    // if we have a value for forceUpdate, it means the bundleId is cacheable and we should store it.
                    preferencesService.javaScriptBundleId = bundleId
                }
                bundleId
            }
        }
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
            environment: AppEnvironment.Environment,
            buildInfo: BuildInfo,
            configService: ConfigService,
            appFramework: AppFramework,
            preferencesService: PreferencesService,
            processStateService: ProcessStateService,
            metadataBackgroundWorker: BackgroundWorker,
            storageStatsManager: StorageStatsManager?,
            windowManager: WindowManager?,
            clock: Clock,
            embraceCpuInfoDelegate: CpuInfoDelegate,
            deviceArchitecture: DeviceArchitecture,
            lazyAppVersionName: Lazy<String>,
            lazyAppVersionCode: Lazy<String>,
            hostedSdkVersionInfo: HostedSdkVersionInfo
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
            val reactNativeBundleId: Future<String?>
            if (appFramework == AppFramework.REACT_NATIVE) {
                reactNativeBundleId = metadataBackgroundWorker.submit<String?> {
                    val lastKnownJsBundleUrl = preferencesService.javaScriptBundleURL
                    val lastKnownJsBundleId = preferencesService.javaScriptBundleId
                    if (!lastKnownJsBundleUrl.isNullOrEmpty() && !lastKnownJsBundleId.isNullOrEmpty()) {
                        // If we have a lastKnownJsBundleId, we use that as the last known bundle ID.
                        return@submit lastKnownJsBundleId
                    } else {
                        // If we don't have a lastKnownJsBundleId, we compute the bundle ID from the last known JS bundle URL.
                        // If the last known JS bundle URL is null, we set React Native bundle ID to the buildId.
                        return@submit computeReactNativeBundleId(
                            context,
                            lastKnownJsBundleUrl,
                            buildInfo.buildId
                        )
                    }
                }
            } else {
                reactNativeBundleId = metadataBackgroundWorker.submit<String?> { buildInfo.buildId }
                logDeveloper("EmbraceMetadataService", "setting default RN as buildId")
            }
            return EmbraceMetadataService(
                windowManager,
                context.packageManager,
                storageStatsManager,
                buildInfo,
                configService,
                environment,
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
                hostedSdkVersionInfo,
                metadataBackgroundWorker,
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
                logError("Failed to retrieve RN bundle file from assets.", e)
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
                logError("Failed to retrieve the custom RN bundle file.", e)
            } catch (e: FileNotFoundException) {
                logError("Failed to retrieve the custom RN bundle file.", e)
            }
            return null
        }

        internal fun computeReactNativeBundleId(
            context: Context,
            bundleUrl: String?,
            defaultBundleId: String?
        ): String? {
            if (bundleUrl == null) {
                // If JS bundle URL is null, we set React Native bundle ID to the defaultBundleId.
                logDeveloper(
                    "EmbraceMetadataService",
                    "bundleUrl is null. Setting default buildId: $defaultBundleId"
                )
                return defaultBundleId
            }

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
