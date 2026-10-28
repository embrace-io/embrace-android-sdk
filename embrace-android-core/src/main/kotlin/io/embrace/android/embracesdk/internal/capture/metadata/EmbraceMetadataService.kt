package io.embrace.android.embracesdk.internal.capture.metadata

import android.annotation.SuppressLint
import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.os.storage.StorageManager
import androidx.annotation.RequiresApi
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.injection.getSystemServiceSafe
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker

/**
 * Provides information about the state of the device, retrieved from Android system services,
 * which is used as metadata with telemetry submitted to the Embrace API.
 */
internal class EmbraceMetadataService(
    private val context: Context,
    private val configService: ConfigService,
    private val clock: Clock,
    private val metadataBackgroundWorker: BackgroundWorker,
    private val versionChecker: VersionChecker = BuildVersionChecker,
) : MetadataService {

    private val statFs by lazy { StatFs(Environment.getDataDirectory().path) }

    @Volatile
    private var diskUsage: DiskUsage? = null

    @Volatile
    private var clockDrift: ClockDrift? = null

    /**
     * Queues in a single thread executor callables to retrieve values in background
     */
    override fun precomputeValues() {
        metadataBackgroundWorker.submit {
            val free = statFs.freeBytes
            if (versionChecker.isAtLeast(Build.VERSION_CODES.O) && configService.config.autoDataCapture.diskUsageCaptureEnabled) {
                val deviceDiskAppUsage = getDeviceDiskAppUsage(
                    context.getSystemServiceSafe(Context.STORAGE_STATS_SERVICE),
                )
                if (deviceDiskAppUsage != null) {
                    diskUsage = DiskUsage(deviceDiskAppUsage, free)
                }
            }
            if (diskUsage == null) {
                diskUsage = DiskUsage(null, free)
            }
            clockDrift = computeClockDrift(
                gnssTimeMillis = @SuppressLint("NewApi") { SystemClock.currentGnssTimeClock().millis() },
                networkTimeMillis = @SuppressLint("NewApi") { SystemClock.currentNetworkTimeClock().millis() },
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getDeviceDiskAppUsage(
        storageStatsManager: StorageStatsManager?,
    ): Long? {
        runCatching {
            if (storageStatsManager != null) {
                val stats = storageStatsManager.queryStatsForPackage(
                    StorageManager.UUID_DEFAULT,
                    context.packageName,
                    Process.myUserHandle(),
                )
                return stats.appBytes + stats.dataBytes + stats.cacheBytes
            }
        }

        // Ignore unrecoverable exception
        return null
    }

    override fun getDiskUsage(): DiskUsage? = diskUsage

    override fun getClockDrift(): ClockDrift? = clockDrift

    internal fun computeClockDrift(gnssTimeMillis: () -> Long, networkTimeMillis: () -> Long): ClockDrift? {
        if (!versionChecker.isAtLeast(Build.VERSION_CODES.Q)) {
            return null
        }

        val gnssDrift = runCatching {
            val gnss = gnssTimeMillis()
            ClockDrift.calculateDrift(wallTimeMillis = clock.now(), auxTimeMillis = gnss)
        }.getOrNull()

        val networkDrift =
            if (versionChecker.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
                runCatching {
                    val network = networkTimeMillis()
                    ClockDrift.calculateDrift(wallTimeMillis = clock.now(), auxTimeMillis = network)
                }.getOrNull()
            } else {
                null
            }

        if (gnssDrift == null && networkDrift == null) {
            return null
        }

        return ClockDrift(networkDriftMillis = networkDrift, gnssDriftMillis = gnssDrift)
    }
}
