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
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * A task that uploads NDK symbols to Embrace.
 */
abstract class NdkUploadTask @Inject constructor(
    objectFactory: ObjectFactory,
) : EmbraceUploadTask, EmbraceUploadTaskImpl(objectFactory) {

    private val logger = Logger(NdkUploadTask::class.java)
    private val serializer = MoshiSerializer()

    @get:Input
    val failBuildOnUploadErrors: Property<Boolean> = objectFactory.property(Boolean::class.java)

    @get:OutputDirectory
    val generatedEmbraceResourcesDirectory: DirectoryProperty = objectFactory.directoryProperty()

    @SkipWhenEmpty
    @get:InputDirectory
    val compressedSharedObjectFilesDirectory: DirectoryProperty = objectFactory.directoryProperty()

    @SkipWhenEmpty
    @get:InputFile
    val architecturesToHashedSharedObjectFilesMapJson: RegularFileProperty = objectFactory.fileProperty()

    private lateinit var architecturesToHashedSharedObjectFilesMap: Map<String, Map<String, String>>

    @TaskAction
    fun onRun() {
        architecturesToHashedSharedObjectFilesMap = getArchToFilenameToHashMap()
        uploadHandshake()
        injectSymbolsAsResources()
    }

    /**
     * Report the files we have available to see which ones the service does not have
     */
    private fun uploadHandshake() {
        val params = NdkUploadHandshakeRequest(
            requestParams.get().appId,
            requestParams.get().apiToken,
            variantData.get().name,
            architecturesToHashedSharedObjectFilesMap
        )
        val networkService = OkHttpNetworkService(requestParams.get().baseUrl)

        NdkUploadHandshake(networkService).getRequestedSymbols(params, failBuildOnUploadErrors.get())?.let { requestedSymbols ->
            uploadSymbols(requestedSymbols)
        }
    }

    private fun getArchToFilenameToHashMap() = try {
        serializer.fromJson(
            architecturesToHashedSharedObjectFilesMapJson.get().asFile.bufferedReader().use { it.readText() },
            ArchitecturesToHashedSharedObjectFilesMap::class.java
        ).architecturesToHashedSharedObjectFiles
    } catch (exception: Exception) {
        error("Failed to read the architectures to hashed shared object files map: ${exception.message}")
    }

    /**
     * Attempt to upload requested symbols per architecture
     */
    private fun uploadSymbols(requestedSymbols: Map<String, List<String>>) {
        val networkService = OkHttpNetworkService(requestParams.get().baseUrl)
        filterRequestedSymbolsFiles(requestedSymbols).forEach { (arch, files) ->
            files.forEach { (id: String, symbolFile: File) ->
                val result = networkService.uploadNdkSymbolFile(
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
     * Filter the symbol files retrieved from build dir based on the required by service.
     *
     * @param requestedSymbols the required symbols
     * @return a map of architectures to hashes to shared object files
     */
    private fun filterRequestedSymbolsFiles(
        requestedSymbols: Map<String, List<String>>,
    ): Map<String, Map<String, File>> {
        val symbolsToUpload = mutableMapOf<String, Map<String, File>>()
        val compressedDir = compressedSharedObjectFilesDirectory.get().asFile

        requestedSymbols.forEach { (arch, symbols) ->
            val requested = mutableMapOf<String, File>()
            val archDir = File(compressedDir, arch)
            // TODO: what happens when a requested symbol is not found?
            val archHashedObjects = architecturesToHashedSharedObjectFilesMap[arch] ?: emptyMap()

            symbols.forEach { symbolName ->
                val compressedFile = File(archDir, symbolName)
                val hash = archHashedObjects[symbolName]
                if (compressedFile.exists() && hash != null) {
                    requested[hash] = compressedFile
                }
            }
            if (requested.isNotEmpty()) {
                symbolsToUpload[arch] = requested
            }
        }

        if (symbolsToUpload.isEmpty()) {
            logger.error("None of the requested symbols were found")
        }

        return symbolsToUpload
    }

    private fun injectSymbolsAsResources() {
        val ndkSymbolsFile = generatedEmbraceResourcesDirectory.dir("values").get().asFile

        try {
            ndkSymbolsFile.delete()
        } catch (e: IOException) {
            logger.info(
                "Failed to delete previous config file for variant=${variantData.get().name} and path=${ndkSymbolsFile.path}"
            )
        }

        val buildInfoFile = File(ndkSymbolsFile, FILE_NDK_SYMBOLS)
        val injector = SymbolResourceInjector()
        injector.writeSymbolResourceFile(buildInfoFile, architecturesToHashedSharedObjectFilesMap)
    }

    companion object {
        const val NAME: String = "ndkUploadTask"
        const val FILE_NDK_SYMBOLS: String = "ndk_symbols.xml"
    }
}
