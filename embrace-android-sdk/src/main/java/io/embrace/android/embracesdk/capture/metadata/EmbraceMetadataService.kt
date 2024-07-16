package io.embrace.android.embracesdk.capture.metadata

import android.annotation.TargetApi
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.util.DisplayMetrics
import android.view.WindowManager
import io.embrace.android.embracesdk.BuildConfig
import io.embrace.android.embracesdk.capture.cpu.CpuInfoDelegate
import io.embrace.android.embracesdk.internal.BuildInfo
import io.embrace.android.embracesdk.internal.DeviceArchitecture
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.payload.AppInfo
import io.embrace.android.embracesdk.payload.DeviceInfo
import io.embrace.android.embracesdk.payload.DiskUsage
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.session.lifecycle.StartupListener
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
    private val systemInfo: SystemInfo,
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
    private val deviceArchitecture: DeviceArchitecture,
    private val logger: EmbLogger
) : MetadataService, StartupListener {

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
            this.reactNativeBundleId = reactNativeBundleId
        } else {
            this.reactNativeBundleId = metadataBackgroundWorker.submit<String?> { buildInfo.buildId }
        }
    }

    /**
     * Queues in a single thread executor callables to retrieve values in background
     */
    override fun precomputeValues() {
        asyncRetrieveIsJailbroken()
        asyncRetrieveScreenResolution()
        asyncRetrieveAdditionalDeviceInfo()

        // Always retrieve the DiskUsage last because it can take the longest to run
        asyncRetrieveDiskUsage(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
    }

    private fun asyncRetrieveAdditionalDeviceInfo() {
        if (!cpuName.isNullOrEmpty() && !egl.isNullOrEmpty()) {
            return
        }
        metadataBackgroundWorker.submit {
            val storedCpuName = preferencesService.cpuName
            val storedEgl = preferencesService.egl
            if (storedCpuName != null) {
                cpuName = storedCpuName
            } else {
                cpuName = embraceCpuInfoDelegate.getCpuName()
                preferencesService.cpuName = cpuName
            }
            if (storedEgl != null) {
                egl = storedEgl
            } else {
                egl = embraceCpuInfoDelegate.getEgl()
                preferencesService.egl = egl
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun asyncRetrieveScreenResolution() {
        // if the screenResolution exists in memory, don't try to retrieve it
        if (!screenResolution.isNullOrEmpty()) {
            return
        }
        metadataBackgroundWorker.submit {
            val storedScreenResolution = preferencesService.screenResolution
            // get from shared preferences
            if (storedScreenResolution != null) {
                screenResolution = storedScreenResolution
            } else if (windowManager != null) {
                screenResolution = try {
                    val display = windowManager.defaultDisplay
                    val displayMetrics = DisplayMetrics()
                    display?.getMetrics(displayMetrics)
                    String.format(Locale.US, "%dx%d", displayMetrics.widthPixels, displayMetrics.heightPixels)
                } catch (ex: Exception) {
                    null
                }
                preferencesService.screenResolution = screenResolution
            }
        }
    }

    private fun asyncRetrieveIsJailbroken() {
        // if the isJailbroken property exists in memory, don't try to retrieve it
        if (isJailbroken != null) {
            return
        }
        metadataBackgroundWorker.submit {
            val storedIsJailbroken = preferencesService.jailbroken
            // load value from shared preferences
            if (storedIsJailbroken != null) {
                isJailbroken = storedIsJailbroken
            } else {
                isJailbroken = isJailbroken()
                preferencesService.jailbroken = isJailbroken
            }
        }
    }

    private fun asyncRetrieveDiskUsage(isAndroid26OrAbove: Boolean) {
        metadataBackgroundWorker.submit {
            val free = MetadataUtils.getInternalStorageFreeCapacity(statFs.value)
            if (isAndroid26OrAbove && configService.autoDataCaptureBehavior.isDiskUsageReportingEnabled()) {
                val deviceDiskAppUsage = getDeviceDiskAppUsage(
                    storageStatsManager,
                    packageManager,
                    packageName
                )
                if (deviceDiskAppUsage != null) {
                    diskUsage = DiskUsage(deviceDiskAppUsage, free)
                }
            }
            if (diskUsage == null) {
                diskUsage = DiskUsage(null, free)
            }
        }
    }

    @Suppress("DEPRECATION")
    @TargetApi(Build.VERSION_CODES.O)
    fun getDeviceDiskAppUsage(
        storageStatsManager: StorageStatsManager?,
        packageManager: PackageManager,
        contextPackageName: String?
    ): Long? {
        try {
            val packageInfo = packageManager.getPackageInfo(contextPackageName!!, 0)
            if (packageInfo?.packageName != null && storageStatsManager != null) {
                val stats = storageStatsManager.queryStatsForPackage(
                    StorageManager.UUID_DEFAULT,
                    packageInfo.packageName,
                    Process.myUserHandle()
                )
                return stats.appBytes + stats.dataBytes + stats.cacheBytes
            }
        } catch (ex: java.lang.Exception) {
            // The package name and storage volume should always exist
            logger.logError("Error retrieving device disk usage", ex)
            logger.trackInternalError(InternalErrorType.DISK_STAT_CAPTURE_FAIL, ex)
        }
        return null
    }

    /**
     * Return the bundle Id if it was already calculated in background or null if it's not ready yet.
     * This way, we avoid blocking the main thread to wait for the value.
     */
    override fun getReactNativeBundleId(): String? =
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
            systemInfo.deviceManufacturer,
            systemInfo.deviceModel,
            deviceArchitecture.architecture,
            isJailbroken(),
            MetadataUtils.getLocale(),
            storageCapacityBytes,
            systemInfo.osName,
            systemInfo.osVersion,
            systemInfo.androidOsApiLevel.toInt(),
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

    override fun getAppId(): String? {
        return configService.sdkModeBehavior.appId
    }

    override fun isAppUpdated(): Boolean = appUpdated.value

    override fun isOsUpdated(): Boolean = osUpdated.value

    override fun getAppState(): String {
        return if (processStateService.isInBackground) {
            "background"
        } else {
            "foreground"
        }
    }

    override fun getDiskUsage(): DiskUsage? = diskUsage

    override fun getScreenResolution(): String? = screenResolution

    override fun isJailbroken(): Boolean? = isJailbroken

    override fun getCpuName(): String? = cpuName

    override fun getEgl(): String? = egl

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
                    buildInfo.buildId,
                    logger
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
        val osVersion = systemInfo.osVersion
        val localDeviceId = getDeviceId()
        val installDate = clock.now()
        logger.logDebug(
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
            systemInfo: SystemInfo,
            buildInfo: BuildInfo,
            configService: ConfigService,
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
            hostedSdkVersionInfo: HostedSdkVersionInfo,
            logger: EmbLogger
        ): EmbraceMetadataService {
            val isAppUpdated = lazy {
                val lastKnownAppVersion = preferencesService.appVersion
                val appUpdated = (
                    lastKnownAppVersion != null &&
                        !lastKnownAppVersion.equals(lazyAppVersionName.value, ignoreCase = true)
                    )
                appUpdated
            }
            val isOsUpdated = lazy {
                val lastKnownOsVersion = preferencesService.osVersion
                val osUpdated = (
                    lastKnownOsVersion != null &&
                        !lastKnownOsVersion.equals(
                            systemInfo.osVersion,
                            ignoreCase = true
                        )
                    )
                osUpdated
            }
            val deviceIdentifier = lazy(preferencesService::deviceIdentifier)
            val reactNativeBundleId: Future<String?>
            if (configService.appFramework == AppFramework.REACT_NATIVE) {
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
                            buildInfo.buildId,
                            logger
                        )
                    }
                }
            } else {
                reactNativeBundleId = metadataBackgroundWorker.submit<String?> { buildInfo.buildId }
            }
            return EmbraceMetadataService(
                windowManager,
                context.packageManager,
                storageStatsManager,
                systemInfo,
                buildInfo,
                configService,
                environment,
                deviceIdentifier,
                context.packageName,
                lazyAppVersionName,
                lazyAppVersionCode,
                configService.appFramework,
                isAppUpdated,
                isOsUpdated,
                preferencesService,
                processStateService,
                reactNativeBundleId,
                hostedSdkVersionInfo,
                metadataBackgroundWorker,
                clock,
                embraceCpuInfoDelegate,
                deviceArchitecture,
                logger
            )
        }

        private fun getBundleAssetName(bundleUrl: String): String {
            return bundleUrl.substring(bundleUrl.indexOf("://") + 3)
        }

        private fun getBundleAsset(context: Context, bundleUrl: String, logger: EmbLogger): InputStream? {
            try {
                return context.assets.open(getBundleAssetName(bundleUrl))
            } catch (e: Exception) {
                logger.logError("Failed to retrieve RN bundle file from assets.", e)
            }
            return null
        }

        private fun getCustomBundleStream(bundleUrl: String, logger: EmbLogger): InputStream? {
            try {
                return FileInputStream(bundleUrl)
            } catch (e: NullPointerException) {
                logger.logError("Failed to retrieve the custom RN bundle file.", e)
            } catch (e: FileNotFoundException) {
                logger.logError("Failed to retrieve the custom RN bundle file.", e)
            }
            return null
        }

        internal fun computeReactNativeBundleId(
            context: Context,
            bundleUrl: String?,
            defaultBundleId: String?,
            logger: EmbLogger
        ): String? {
            if (bundleUrl == null) {
                // If JS bundle URL is null, we set React Native bundle ID to the defaultBundleId.
                return defaultBundleId
            }

            val bundleStream: InputStream?

            // checks if the bundle url is an asset
            if (bundleUrl.contains("assets")) {
                // looks for the bundle file in assets
                bundleStream = getBundleAsset(context, bundleUrl, logger)
            } else {
                // looks for the bundle file from the custom path
                bundleStream = getCustomBundleStream(bundleUrl, logger)
            }
            if (bundleStream == null) {
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
                logger.logError("Failed to compute the RN bundle file.", e)
            }
            // if the hashing of the JS bundle URL fails, returns the default bundle ID
            return defaultBundleId
        }

        private fun hashBundleToMd5(bundle: ByteArray): String {
            val hashBundle: String
            val md = MessageDigest.getInstance("MD5")
            val bundleHashed = md.digest(bundle)
            val sb = StringBuilder()
            for (b in bundleHashed) {
                sb.append(String.format(Locale.getDefault(), "%02x", b.toInt() and 0xff))
            }
            hashBundle = sb.toString().toUpperCase(Locale.getDefault())
            return hashBundle
        }
    }
}
