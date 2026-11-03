package io.embrace.android.embracesdk.internal.storage

import android.content.Context
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.semconv.EmbTelemetryAttributes
import java.io.File
import java.io.FilenameFilter

/**
 * Measures the storage the SDK uses, for storage telemetry.
 */
@Suppress("EmbraceContextDirAccess")
class EmbraceStorageService(
    private val context: Context,
    private val telemetryService: InternalTelemetryService,
    private val storageAvailabilityChecker: StorageAvailabilityChecker,
) : StorageService {

    private val cacheDirectory: File by lazy {
        context.cacheDir
    }

    private val filesDirectory: File by lazy {
        getOrCreateEmbraceFilesDir() ?: cacheDirectory
    }

    override fun listFiles(filter: FilenameFilter): List<File> {
        val filesDir = filesDirectory.listFiles(filter) ?: emptyArray()
        val cacheDir = cacheDirectory.listFiles(filter) ?: emptyArray()
        return filesDir.toList() + cacheDir.toList()
    }

    /**
     * Logs storage telemetry, including the current size utilized by Embrace.
     */
    override fun logStorageTelemetry() {
        val availableStorage = storageAvailabilityChecker.getAvailableBytes()
        val storageUsed = listFiles { _, _ -> true }
            .filter { it.isFile }
            .sumOf { it.length() }
        val storageTelemetryMap = mapOf(
            EmbTelemetryAttributes.EMB_STORAGE_USED to storageUsed.toString(),
            EmbTelemetryAttributes.EMB_STORAGE_AVAILABLE to availableStorage.toString(),
        )
        telemetryService.logStorageTelemetry(storageTelemetryMap)
    }

    /**
     * Get or create the Embrace folder inside the files directory.
     */
    private fun getOrCreateEmbraceFilesDir(): File? {
        val filesDir = File(context.filesDir, EMBRACE_DIRECTORY)
        return try {
            filesDir.mkdirs()
            filesDir.takeIf { it.exists() }
        } catch (e: SecurityException) {
            null
        }
    }
}

/**
 * Directory name for files that are stored in the files directory.
 */
private const val EMBRACE_DIRECTORY = "embrace"
