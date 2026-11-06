package io.embrace.android.embracesdk.internal.storage

import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.semconv.EmbTelemetryAttributes
import okio.IOException
import okio.Path

/**
 * Reports how many bytes the SDK stores and how many are free on the volume it stores them on.
 */
class StorageTelemetry(
    private val storage: SdkStorage,
    private val telemetryService: InternalTelemetryService,
    private val storageAvailabilityChecker: StorageAvailabilityChecker,
) {

    fun log() {
        telemetryService.logStorageTelemetry(
            mapOf(
                EmbTelemetryAttributes.EMB_STORAGE_USED to bytesUsed().toString(),
                EmbTelemetryAttributes.EMB_STORAGE_AVAILABLE to storageAvailabilityChecker.getAvailableBytes().toString(),
            ),
        )
    }

    private fun bytesUsed(): Long = LEAF_DIRS.sumOf { bytesUnder(storage.path(it)) }

    private fun bytesUnder(dir: Path): Long {
        val fs = storage.fileSystem
        if (fs.metadataOrNull(dir)?.isDirectory != true) {
            return 0
        }
        var total = 0L
        try {
            fs.listRecursively(dir).forEach { path ->
                val metadata = fs.metadataOrNull(path)
                if (metadata?.isRegularFile == true) {
                    total += metadata.size ?: 0
                }
            }
        } catch (ignored: IOException) {
        }
        return total
    }

    private companion object {
        val LEAF_DIRS: List<SdkDir> = SdkDir.entries.filter { dir -> SdkDir.entries.none { it.parent == dir } }
    }
}
