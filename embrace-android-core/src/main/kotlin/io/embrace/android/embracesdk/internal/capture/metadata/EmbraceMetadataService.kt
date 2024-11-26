package io.embrace.android.embracesdk.internal.capture.metadata

import android.annotation.TargetApi
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker

/**
 * Provides information about the state of the device, retrieved from Android system services,
 * which is used as metadata with telemetry submitted to the Embrace API.
 */
internal class EmbraceMetadataService(
    resourceSource: Lazy<EnvelopeResourceSource>,
    private val context: Context,
    private val storageStatsManager: Lazy<StorageStatsManager?>,
    private val configService: ConfigService,
    private val preferencesService: PreferencesService,
    private val metadataBackgroundWorker: BackgroundWorker,
    private val clock: Clock,
    private val logger: EmbLogger,
) : MetadataService {

    private val res by lazy { resourceSource.value.getEnvelopeResource() }

    private val statFs by lazy { StatFs(Environment.getDataDirectory().path) }

    @Volatile
    private var diskUsage: DiskUsage? = null

    /**
     * Queues in a single thread executor callables to retrieve values in background
     */
    override fun precomputeValues() {
        metadataBackgroundWorker.submit {
            with(preferencesService) {
                appVersion = res.appVersion
                osVersion = res.osVersion
                if (installDate == null) {
                    installDate = clock.now()
                }
            }
            val free = statFs.freeBytes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && configService.autoDataCaptureBehavior.isDiskUsageCaptureEnabled()) {
                val deviceDiskAppUsage = getDeviceDiskAppUsage(
                    storageStatsManager.value,
                    context.packageManager,
                    context.packageName
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
    private fun getDeviceDiskAppUsage(
        storageStatsManager: StorageStatsManager?,
        packageManager: PackageManager,
        contextPackageName: String?,
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
        } catch (ex: Exception) {
            // The package name and storage volume should always exist
            logger.trackInternalError(InternalErrorType.DISK_STAT_CAPTURE_FAIL, ex)
        }
        return null
    }

    override fun getDiskUsage(): DiskUsage? = diskUsage
}
