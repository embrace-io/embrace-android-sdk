package io.embrace.android.gradle.plugin.tasks.il2cpp

import io.embrace.android.gradle.plugin.Logger
import io.embrace.android.gradle.plugin.config.UnitySymbolsDir
import io.embrace.android.gradle.plugin.instrumentation.config.model.UnityConfig
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import org.gradle.api.file.Directory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipInputStream

private const val BUFFER_SIZE = 2048
private const val UNITY_SYMBOLS_NAME = "symbols"
private const val UNITY_SYMBOLS_SUFFIX_NAME = ".zip"
private const val UNITY_SYMBOL_EXTENSION_2019 = ".sym.so"
private const val UNITY_SYMBOL_IL2CPP_EXTENSION_2018 = ".sym"
private const val UNITY_SYMBOLS_DEFAULT_DIR_NAME = "StagingArea"
private const val UNITY_LIBRARY_DEFAULT_DIR_NAME = "unityLibrary"

internal class UnitySymbolFilesManager {
    private val logger = Logger(UnitySymbolFilesManager::class.java)

    companion object Factory {
        private lateinit var INSTANCE: UnitySymbolFilesManager

        fun of(): UnitySymbolFilesManager {
            if (!::INSTANCE.isInitialized) {
                INSTANCE = UnitySymbolFilesManager()
            }

            return INSTANCE
        }
    }

    /**
     * Gets the directory where the symbols are located.
     * 1. Look for exported unity symbols zip file.
     * 2. Look if exported_project/unityLibrary/symbols directory exists.
     * 3. Look if Unity temp build directory (StagingArea/Symbols) exists.
     */
    fun getSymbolsDir(
        realProjectDirectory: Directory,
        projectDirectory: Directory,
        unityConfig: UnityConfig?
    ): UnitySymbolsDir {
        val customArchiveName = unityConfig?.symbolsArchiveName?.takeIf { it.isNotEmpty() }
        val unitySymbolsArchiveFile = getUnitySymbolsArchive(projectDirectory, customArchiveName)
        if (unitySymbolsArchiveFile != null) {
            return UnitySymbolsDir(unitySymbolsArchiveFile, true)
        }

        // If no archive file is found, return the Unity symbols directory or failing that, the Unity temp directory
        val unitySymbolsDir = getUnitySymbolsDirectory(
            projectDirectory,
            UNITY_LIBRARY_DEFAULT_DIR_NAME
        ) ?: getUnitySymbolsDirectory(
            realProjectDirectory,
            UNITY_SYMBOLS_DEFAULT_DIR_NAME
        )

        return if (unitySymbolsDir != null) {
            UnitySymbolsDir(unitySymbolsDir, false)
        } else {
            logger.error(
                "No Unity symbols found for project at path=${realProjectDirectory.asFile.absolutePath}. " +
                    "The project is not a Unity project or the symbols file was not exported."
            )
            UnitySymbolsDir()
        }
    }

    /**
     * Given the directory where the symbols files are stored, get the list of symbol files.
     */
    fun getSymbolFiles(
        unitySymbolsDir: UnitySymbolsDir,
        buildDir: Directory,
        variantData: AndroidCompactedVariantData
    ): Array<File> {
        val symbolsDir = unitySymbolsDir.unitySymbolsDir ?: return emptyArray()

        return if (unitySymbolsDir.isDirPresent() && unitySymbolsDir.zippedSymbols) {
            extractSoFilesFromZipFile(
                symbolsDir,
                buildDir,
                variantData
            )
        } else {
            val files = symbolsDir.listFiles()
            if (files == null) {
                logger.info("No symbol files found in Unity symbols directory at path: ${symbolsDir.path}")
                emptyArray()
            } else {
                logger.info("Using symbols from Unity symbols directory at path: ${symbolsDir.path}")
                files
            }
        }
    }

    /**
     * Get Unity exported .zip file with symbols.
     */
    private fun getUnitySymbolsArchive(
        projectDir: Directory,
        customArchiveName: String?
    ): File? {
        val parentDir = projectDir.asFile.parentFile ?: return null
        // Search symbols zip file at same level of exported project folder
        val defaultArchiveName = parentDir.name
        val projectParent = parentDir.parentFile
        val symbolsArchive = projectParent?.searchSymbolsArchive(
            defaultArchiveName = defaultArchiveName,
            customArchiveName = customArchiveName
        ) ?: projectParent.parentFile?.searchSymbolsArchive(
            defaultArchiveName = defaultArchiveName,
            customArchiveName = customArchiveName
        )

        if (symbolsArchive != null) {
            logger.info("Unity symbols archive found at path: ${symbolsArchive.path}")
        }

        return symbolsArchive
    }

    private fun File.searchSymbolsArchive(
        defaultArchiveName: String,
        customArchiveName: String?
    ): File? {
        // Use the last file found that matches the criteria for being a Unity symbols file
        var foundFile: File? = null
        try {
            listFiles()?.forEach { file ->
                if (customArchiveName != null &&
                    file.name.startsWith(customArchiveName) &&
                    file.name.endsWith(UNITY_SYMBOLS_SUFFIX_NAME)
                ) {
                    foundFile = file
                } else if (file.name.contains(UNITY_SYMBOLS_NAME) &&
                    file.name.endsWith(UNITY_SYMBOLS_SUFFIX_NAME) &&
                    file.name.startsWith(defaultArchiveName)
                ) {
                    foundFile = file
                }
            }
        } catch (ex: Exception) {
            logger.warn("Unexpected error searching for Unity symbols archive.", ex)
        }

        if (foundFile == null) {
            logger.info("Could not find symbols archive at path: $this}")
        }

        return foundFile
    }

    private fun getUnitySymbolsDirectory(projectDirectory: Directory, subDirName: String): File? {
        return try {
            val dir = projectDirectory.asFile.parentFile ?: return null
            val symbolsDir = Paths.get(
                dir.path,
                subDirName,
                UNITY_SYMBOLS_NAME
            ).toFile()

            if (symbolsDir.exists()) {
                symbolsDir
            } else {
                logger.info("Could not find Unity symbols directory at path ${symbolsDir.path}")
                null
            }
        } catch (e: UnsupportedOperationException) {
            logger.warn("Unexpected error searching for Unity symbols directory.", e)
            null
        }
    }

    private fun extractSoFilesFromZipFile(
        objFolder: File,
        buildDir: Directory,
        variantData: AndroidCompactedVariantData
    ): Array<File> {
        val decompressedFile = File(getUncompressedUnityFilesPath(buildDir, variantData))

        // Remove previous files from build intermediates folder
        try {
            if (decompressedFile.exists()) {
                decompressedFile.listFiles()?.forEach { archDir ->
                    archDir.listFiles()?.forEach { arch ->
                        logger.info("Deleting existing symbol file: ${arch.path}")
                        Files.delete(arch.toPath())
                    }
                }
            }

            decompressedFile.mkdirs()
        } catch (ex: IOException) {
            logger.warn(
                "Failed to delete previous Unity symbol files from build intermediates directory: ${decompressedFile.absolutePath}",
                ex
            )
        }

        val buffer = ByteArray(BUFFER_SIZE)
        val outDir: Path = Paths.get(getUncompressedUnityFilesPath(buildDir, variantData))

        return try {
            FileInputStream(objFolder).use { fis ->
                BufferedInputStream(fis).use { bis ->
                    ZipInputStream(bis).use { stream ->
                        processZipStream(stream, outDir, buffer, decompressedFile).also {
                            logger.info("Using symbols extracted from archive at path: ${objFolder.path}")
                        }
                    }
                }
            }
        } catch (ex: IOException) {
            logger.error(
                "Failed to decompress Unity symbols for file in path ${objFolder.path}.",
                ex
            )
            emptyArray()
        }
    }

    private fun processZipStream(
        stream: ZipInputStream,
        outDir: Path,
        buffer: ByteArray,
        decompressedFile: File
    ): Array<File> {
        var entry = stream.nextEntry
        while (entry != null) {
            val filePath = outDir.resolve(entry.name)

            // This is true for Unity 2018 and 2019 which is no longer supported.
            // Can remove after automation put in place that it's not used in some other code path for newer, supported Unity versions.
            if (!entry.isDirectory) {
                val name = entry.name
                if (name.endsWith(UNITY_SYMBOL_EXTENSION_2019) || name.endsWith(UNITY_SYMBOL_IL2CPP_EXTENSION_2018)) {
                    logger.info("Extracting symbols from a non-directory archive with the name $name")
                    FileOutputStream(filePath.toFile()).use { fos ->
                        BufferedOutputStream(fos, buffer.size).use { bos ->
                            var len: Int
                            while (stream.read(buffer).also { len = it } > 0) {
                                bos.write(buffer, 0, len)
                            }
                        }
                    }
                }
            } else {
                filePath.toFile().mkdirs()
            }
            entry = stream.nextEntry
        }
        return decompressedFile.listFiles() ?: emptyArray()
    }

    private fun getUncompressedUnityFilesPath(
        buildDir: Directory,
        variantData: AndroidCompactedVariantData
    ): String {
        return "${buildDir.asFile.absoluteFile}/intermediates/embrace/unity/${getMappingFileFolder(variantData)}"
    }

    private fun getMappingFileFolder(variantData: AndroidCompactedVariantData): String {
        return if (variantData.flavorName.isBlank()) {
            variantData.buildTypeName
        } else {
            "${variantData.flavorName}/${variantData.buildTypeName}"
        }
    }
}
