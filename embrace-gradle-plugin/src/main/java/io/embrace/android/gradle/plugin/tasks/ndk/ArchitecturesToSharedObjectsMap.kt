package io.embrace.android.gradle.plugin.tasks.ndk

import com.squareup.moshi.JsonClass

/**
 * Data class representing the mapping of architectures to their shared object files.
 * This structure is used for serialization between tasks.
 */
@JsonClass(generateAdapter = true)
data class ArchitecturesToSharedObjectsMap(
    val architecturesToSharedObjects: Map<String, List<String>>
)
