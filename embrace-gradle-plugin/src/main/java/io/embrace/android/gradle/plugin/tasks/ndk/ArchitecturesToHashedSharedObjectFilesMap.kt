package io.embrace.android.gradle.plugin.tasks.ndk

import com.squareup.moshi.JsonClass

/**
 * Data class representing a mapping of architectures to their shared object files and corresponding hashes.
 * The structure is:
 * - Keys are architecture names (e.g., "arm64-v8a")
 * - Values are maps where:
 *   - Keys are shared object filenames (e.g., "libexample1.so")
 *   - Values are SHA1 hashes of the compressed files
 */
@JsonClass(generateAdapter = true)
data class ArchitecturesToHashedSharedObjectFilesMap(
    val architecturesToHashedSharedObjectFiles: Map<String, Map<String, String>>
)
