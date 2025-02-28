package io.embrace.android.gradle.plugin.tasks.ndk

import io.embrace.android.gradle.plugin.Logger
import io.embrace.android.gradle.plugin.config.ProjectType
import io.embrace.android.gradle.plugin.config.UnitySymbolsDir
import io.embrace.android.gradle.plugin.hash.calculateSha1ForFile
import io.embrace.android.gradle.plugin.network.OkHttpNetworkService
import io.embrace.android.gradle.plugin.tasks.EmbraceUploadTask
import io.embrace.android.gradle.plugin.tasks.EmbraceUploadTaskImpl
import io.embrace.android.gradle.plugin.tasks.il2cpp.UnitySymbolFilesManager
import io.embrace.android.gradle.plugin.util.compression.ZstdFileCompressor
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.UnsupportedEncodingException
import javax.inject.Inject
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.TransformerException

/**
 * A task that uploads NDK symbols to Embrace.
 */
abstract class NdkUploadTask @Inject constructor(
    objectFactory: ObjectFactory,
) : EmbraceUploadTask, EmbraceUploadTaskImpl(objectFactory) {

    private val logger = Logger(NdkUploadTask::class.java)

    private val deobfuscatedHashedObjects: MutableMap<String, Map<String, String>> = HashMap()

    private val unitySymbolFilesManager = UnitySymbolFilesManager.of()

    @get:Optional
    @get:Input
    val projectType: Property<ProjectType> = objectFactory.property(ProjectType::class.java).convention(ProjectType.OTHER)

    @get:Internal
    val deobfuscatedFilesDirPath: DirectoryProperty = objectFactory.directoryProperty()

    @get:Input
    @get:Optional
    val unitySymbolsDir: Property<UnitySymbolsDir?> = objectFactory.property(
        UnitySymbolsDir::class.java
    )

    @get:Input
    val ndkEnabled: Property<Boolean> = objectFactory.property(Boolean::class.java)

    @get:OutputDirectory
    val generatedEmbraceResourcesDirectory: DirectoryProperty = objectFactory.directoryProperty()

    @get:InputFiles
    @get:SkipWhenEmpty
    val architecturesDirectoryForNative: ConfigurableFileCollection = objectFactory.fileCollection()

    @Inject
    open fun getProjectLayout(): ProjectLayout {
        throw UnsupportedOperationException()
    }

    @TaskAction
    fun onRun() {
        if (!ndkEnabled.get()) {
            logger.warn("NDK upload task will not run when the NDK is disabled.")
            return
        }

        try {
            generateHashedObjects()
            uploadHandshake()
            injectSymbolsAsResources()
        } catch (ex: Exception) {
            val msg = "Failed uploading mapping artifact."
            logger.error(msg)
            throw IllegalStateException(msg, ex)
        }
    }

    @Throws(IllegalArgumentException::class)
    private fun generateHashedObjects() {
        // retrieve .so files by architecture
        getSoFilesByArchitecture().forEach { (arch, sharedObjects) ->
            val hashedObjects = mutableMapOf<String, String>()
            sharedObjects.forEach { sharedObject ->
                try {
                    val outputDir = deobfuscatedFilesDirPath.dir(arch).get().asFile.absolutePath
                    val zstdFileCompressor = ZstdFileCompressor()
                    val compressedFile = zstdFileCompressor.compress(
                        sharedObject,
                        File(outputDir, sharedObject.name)
                    )
                    if (compressedFile != null) {
                        // add hashed file by architecture
                        hashedObjects[compressedFile.name] = calculateSha1ForFile(compressedFile)
                    }
                } catch (ex: Throwable) {
                    logger.error("Failed to generate hash for ${sharedObject.name} object.", ex)
                }
            }
            if (hashedObjects.isNotEmpty()) {
                deobfuscatedHashedObjects[arch] = hashedObjects
            } else {
                logger.error("Failed to generate hashed objects for any architectures")
            }
        }
    }

    private fun getSoFilesByArchitecture(): Map<String, List<File>> {
        val archFiles = when (projectType.get()) {
            ProjectType.UNITY -> getSoFilesByArchitectureForUnity()
            ProjectType.NATIVE -> getSoFilesByArchitectureForNative()
            else -> throw IllegalArgumentException("Cannot generate NDK map file. Unsupported NDK type.")
        }

        return generateArchSoMap(archFiles)
    }

    private fun getSoFilesByArchitectureForUnity(): Array<File> {
        val symbolsDir = unitySymbolsDir.orNull
        val unitySymbolFiles = if (this.unitySymbolsDir.isPresent && symbolsDir != null) {
            unitySymbolFilesManager.getSymbolFiles(
                symbolsDir,
                getProjectLayout().buildDirectory.get(),
                variantData.get()
            )
        } else {
            emptyArray()
        }

        if (unitySymbolFiles.isEmpty()) {
            logger.error("Unity symbol files not found")
        }

        return unitySymbolFiles
    }

    private fun getSoFilesByArchitectureForNative(): Array<File> {
        val files = if (!architecturesDirectoryForNative.isEmpty) {
            architecturesDirectoryForNative.singleFile.listFiles() ?: emptyArray()
        } else {
            emptyArray()
        }

        if (files.isEmpty()) {
            logger.error("No mapping files found for native NDK")
        }

        return files
    }

    /**
     * Generates a map of .so files based on architectures.
     *
     * @param listOfArch list of architectures files to process
     */
    private fun generateArchSoMap(listOfArch: Array<File>): HashMap<String, List<File>> {
        val archSoMap = HashMap<String, List<File>>()

        listOfArch
            .filter { it.exists() }
            .forEach { arch ->
                arch.listFiles { _, name -> name.endsWith(".so") }?.toList()?.let { soFileList ->
                    archSoMap[arch.name] = soFileList
                    soFileList.forEach {
                        logger.info("Symbol file found for arch ${arch.name} at path ${it.path}")
                    }
                }
            }

        return archSoMap
    }

    /**
     * Report the files we have available to see which ones the service does not have
     */
    private fun uploadHandshake() {
        val params = NdkUploadHandshakeRequest(
            requestParams.get().appId,
            requestParams.get().apiToken,
            variantData.get().name,
            deobfuscatedHashedObjects
        )
        val networkService = OkHttpNetworkService(requestParams.get().baseUrl)

        NdkUploadHandshake(networkService).getRequestedSymbols(params)?.let { requestedSymbols ->
            uploadSymbols(requestedSymbols)
        }
    }

    /**
     * Attempt to upload requested symbols per architecture
     */
    private fun uploadSymbols(requestedSymbols: Map<String, List<String>>) {
        filterRequestedSymbolsFiles(requestedSymbols).forEach { (arch, files) ->
            files.forEach { (id: String, symbolFile: File) ->
                try {
                    OkHttpNetworkService(requestParams.get().baseUrl).uploadNdkSymbolFile(
                        params = requestParams.get().copy(fileName = symbolFile.name),
                        file = symbolFile,
                        variantName = variantData.get().name,
                        arch = arch,
                        id = id,
                    )
                } catch (ex: Exception) {
                    logger.error(
                        "An exception occurred when attempting to upload symbols ${symbolFile.getName()} for arch $arch.",
                        ex
                    )
                }
            }
        }
    }

    /**
     * Filter the symbol files retrieved from build dir based on the required by service.
     *
     * @param requestedSymbols the required symbols
     * @return a map of requested symbols grouped by architecture
     */
    private fun filterRequestedSymbolsFiles(
        requestedSymbols: Map<String, List<String>>,
    ): Map<String, Map<String, File>> {
        val selectedSymbols = mutableMapOf<String, Map<String, File>>()
        getDeobfuscatedSymbolsFiles()?.let { deobfuscatedSymbols ->
            requestedSymbols.forEach { (arch, symbols) ->
                val requested = mutableMapOf<String, File>()
                deobfuscatedSymbols[arch]?.let { symbolFiles ->
                    symbolFiles.forEach { symbolFile ->
                        if (symbols.contains(symbolFile.name)) {
                            getSha1ByFile(symbolFile, arch)?.let { sha1 ->
                                requested[sha1] = symbolFile
                            }
                        }
                    }
                    if (requested.isNotEmpty()) {
                        selectedSymbols[arch] = requested
                    }
                }

                if (requested.isEmpty()) {
                    logger.warn("None of the requested symbols for architecture $arch were found")
                }
            }
        }

        if (selectedSymbols.isEmpty()) {
            logger.error("None of the requested symbols were found")
        }

        return selectedSymbols
    }

    /**
     * Get sha1 hash for the provided symbol file.
     *
     * @return sha1 hash
     */
    private fun getSha1ByFile(symbolFile: File, archName: String): String? {
        val sha1Value = deobfuscatedHashedObjects[archName]?.get(symbolFile.name)
        if (sha1Value != null) {
            logger.info("SHA1 found for architecture $archName for at ${symbolFile.path}: $sha1Value")
        } else {
            logger.info("SHA1 not found for architecture $archName for at ${symbolFile.path}")
        }

        return sha1Value
    }

    /**
     * Retrieve symbols files by architecture from build dir.
     *
     * @return symbols files
     */
    private fun getDeobfuscatedSymbolsFiles(): Map<String, List<File>>? {
        val symbolsDir = File(deobfuscatedFilesDirPath.asFile.get().absolutePath)

        if (!symbolsDir.exists()) {
            logger.error("Deobfuscation directory does not exist, will return an empty map")
            return null
        }

        val archSoMap = mutableMapOf<String, List<File>>()
        symbolsDir.listFiles()
            ?.filter { it.exists() }
            ?.forEach { arch: File ->
                val archName = arch.name
                val soFiles = arch.listFiles()
                if (soFiles != null) {
                    archSoMap[archName] = soFiles.toList()
                } else {
                    logger.warn("No symbol files for architecture=$archName")
                }
            }

        if (archSoMap.isEmpty()) {
            logger.error("No symbol files are found")
        }

        return archSoMap
    }

    @Throws(
        FileNotFoundException::class,
        UnsupportedEncodingException::class,
        TransformerException::class,
        ParserConfigurationException::class
    )
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
        injector.writeSymbolResourceFile(buildInfoFile, deobfuscatedHashedObjects)
    }

    companion object {
        const val NAME: String = "ndkUploadTask"
        const val FILE_NDK_SYMBOLS: String = "ndk_symbols.xml"
    }
}
