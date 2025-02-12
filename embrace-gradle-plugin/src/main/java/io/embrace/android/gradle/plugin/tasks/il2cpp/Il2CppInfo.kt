package io.embrace.android.gradle.plugin.tasks.il2cpp

import io.embrace.android.gradle.plugin.network.EmbraceEndpoint

/**
 * Holds information relating to a single IL2CPP symbol file.
 */
internal sealed class Il2CppInfo(
    name: String,
    extension: String,
    val endpoint: EmbraceEndpoint
) {

    /**
     * Information relating to the line number mapping file.
     */
    object LineNumberMap :
        Il2CppInfo("LineNumberMappings", "json", EmbraceEndpoint.LINE_MAP)

    /**
     * Information relating to the method mapping file.
     */
    object MethodMap : Il2CppInfo("MethodMap", "tsv", EmbraceEndpoint.METHOD_MAP)

    /**
     * Task name used for compressing IL2CPP symbol files.
     */
    val compressionTaskName = "il2cppCompressionTask$name"

    /**
     * Task name used for uploading IL2CPP symbol files.
     */
    val uploadTaskName = "il2cppUploadTask$name"

    /**
     * The name of the uncompressed IL2CPP symbol file.
     */
    val filename = "$name.$extension"
}
