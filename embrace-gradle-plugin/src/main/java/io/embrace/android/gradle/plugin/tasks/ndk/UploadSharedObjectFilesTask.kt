package io.embrace.android.gradle.plugin.tasks.ndk

import io.embrace.android.gradle.plugin.Logger
import io.embrace.android.gradle.plugin.network.OkHttpNetworkService
import io.embrace.android.gradle.plugin.tasks.EmbraceUploadTask
import io.embrace.android.gradle.plugin.tasks.EmbraceUploadTaskImpl
import io.embrace.android.gradle.plugin.tasks.handleHttpCallResult
import io.embrace.android.gradle.plugin.util.serialization.MoshiSerializer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

/**
 * This task handles the upload of shared object (.so) files to the Embrace backend.
 *
 * Its most important inputs are:
 * - compressedSharedObjectFilesDirectory: Directory containing the compressed .so files.
 * - architecturesToHashedSharedObjectFilesMapJson: JSON mapping architectures to file hashes.
 *
 * Given those inputs, the task will:
 * - Report the available shared object files to the backend.
 * - Find the shared object files requested by the backend (if any) in the compressed files directory.
 * - Upload the requested files individually to the backend.
 */
abstract class UploadSharedObjectFilesTask @Inject constructor(
    objectFactory: ObjectFactory,
) : EmbraceUploadTask, EmbraceUploadTaskImpl(objectFactory) {

    private val serializer = MoshiSerializer()
    private val logger = Logger(UploadSharedObjectFilesTask::class.java)

    @get:Input
    val failBuildOnUploadErrors: Property<Boolean> = objectFactory.property(Boolean::class.java)

    @SkipWhenEmpty
    @get:InputDirectory
    val compressedSharedObjectFilesDirectory: DirectoryProperty = objectFactory.directoryProperty()

    @SkipWhenEmpty
    @get:InputFile
    val architecturesToHashedSharedObjectFilesMapJson: RegularFileProperty = objectFactory.fileProperty()

    private lateinit var architecturesToHashedSharedObjectFilesMap: Map<String, Map<String, String>>

    private val okHttpNetworkService by lazy {
        OkHttpNetworkService(requestParams.get().baseUrl)
    }

    @TaskAction
    fun onRun() {
        try {
            architecturesToHashedSharedObjectFilesMap = getArchToFilenameToHashMap()
            val requestedSharedObjectFiles = getRequestedSharedObjectFiles().takeIf { it.isNotEmpty() } ?: return
            val foundRequestedSharedObjectFiles = findRequestedSharedObjectFiles(requestedSharedObjectFiles)
            uploadSharedObjectFiles(foundRequestedSharedObjectFiles)
        } catch (exception: Exception) {
            logger.error("An error has occurred while uploading shared object files", exception)
            if (failBuildOnUploadErrors.get()) {
                throw exception
            }
        }
    }

    private fun getArchToFilenameToHashMap() = try {
        serializer.fromJson(
            architecturesToHashedSharedObjectFilesMapJson.get().asFile.bufferedReader().use { it.readText() },
            ArchitecturesToHashedSharedObjectFilesMap::class.java
        ).symbols
    } catch (exception: Exception) {
        error("Failed to read the architectures to hashed shared object files map: ${exception.message}")
    }

    /**
     * Report the files we have available, and let the backend tell us which ones it needs
     *
     * @return a map of architectures to requested symbols
     */
    private fun getRequestedSharedObjectFiles(): Map<String, List<String>> {
        val params = NdkUploadHandshakeRequest(
            requestParams.get().appId,
            requestParams.get().apiToken,
            variantData.get().name,
            architecturesToHashedSharedObjectFilesMap
        )

        val handshakeResult = NdkUploadHandshake(okHttpNetworkService).getRequestedSymbols(params, failBuildOnUploadErrors.get())
            ?: error("There was an error while reporting shared object files")
        return handshakeResult
    }

    /**
     * Attempt to upload requested symbols per architecture
     */
    private fun uploadSharedObjectFiles(requestedSharedObjectFiles: Map<String, Map<String, File>>) {
        // TODO: We're spanning multiple http requests, shouldn't we batch these? Having a single request per file doesn't look optimal.
        requestedSharedObjectFiles.forEach { (arch, files) ->
            files.forEach { (id: String, symbolFile: File) ->
                val result = okHttpNetworkService.uploadNdkSymbolFile(
                    params = requestParams.get().copy(fileName = symbolFile.name),
                    file = symbolFile,
                    variantName = variantData.get().name,
                    arch = arch,
                    id = id,
                )
                handleHttpCallResult(result, requestParams.get())
            }
        }
    }

    /**
     * Search for the shared object files requested by the backend.
     * This maps the requested shared object files hashes to the actual files.
     *
     * @param requestedSharedObjectFiles a map of architectures to a list of filenames of shared object files.
     * @return a map of architectures to hashes to the actual shared object files.
     */
    private fun findRequestedSharedObjectFiles(
        requestedSharedObjectFiles: Map<String, List<String>>,
    ): Map<String, Map<String, File>> {
        val symbolsToUpload = mutableMapOf<String, Map<String, File>>()
        val compressedDir = compressedSharedObjectFilesDirectory.get().asFile

        requestedSharedObjectFiles.forEach { (arch, symbols) ->
            val requested = mutableMapOf<String, File>()
            val archHashedObjects = architecturesToHashedSharedObjectFilesMap[arch]
                ?: error("Requested architecture was not found")

            symbols.ifEmpty { error("An arch with no symbols was requested") }
                .forEach { symbolName ->
                    val compressedFile = File("$compressedDir/$arch", symbolName)
                    val hash = archHashedObjects[symbolName]
                    if (hash != null && compressedFile.exists()) {
                        requested[hash] = compressedFile
                    } else {
                        error("Compressed file not found for requested symbol: $arch/$symbolName")
                    }
                }
            symbolsToUpload[arch] = requested
        }

        return symbolsToUpload
    }

    companion object {
        const val NAME: String = "uploadSharedObjectFilesTask"
    }
}
