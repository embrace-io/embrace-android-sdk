package io.embrace.android.embracesdk.internal.storage

import java.io.File
import java.io.FilenameFilter

/**
 * Measures the storage the SDK uses.
 */
interface StorageService {

    /**
     * Returns a list of files from the files and cache directories that match the [filter].
     */
    fun listFiles(filter: FilenameFilter = FilenameFilter { _, _ -> true }): List<File>

    /**
     * Logs storage telemetry such as the currently used size.
     */
    fun logStorageTelemetry()
}
