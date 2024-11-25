package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.ndk.jni.JniDelegate
import io.embrace.android.embracesdk.internal.ndk.symbols.SymbolService
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.storage.NATIVE_CRASH_FILE_FOLDER
import io.embrace.android.embracesdk.internal.storage.StorageService
import java.io.File
import java.io.FileNotFoundException
import java.io.FilenameFilter

internal class NativeCrashProcessorImpl(
    private val sharedObjectLoader: SharedObjectLoader,
    private val logger: EmbLogger,
    private val delegate: JniDelegate,
    private val serializer: PlatformSerializer,
    private val symbolService: SymbolService,
    private val storageService: StorageService,
) : NativeCrashProcessor {

    override fun getLatestNativeCrash(): NativeCrashData? =
        getAllNativeCrashes(::deleteFiles).lastOrNull()

    override fun getNativeCrashes(): List<NativeCrashData> = getAllNativeCrashes()

    override fun deleteAllNativeCrashes() {
        getAllNativeCrashes(::deleteFiles)
    }

    private fun getAllNativeCrashes(
        cleanup: CleanupFunction? = null,
    ): List<NativeCrashData> {
        if (!sharedObjectLoader.loaded.get()) {
            return emptyList()
        }
        val nativeCrashes = sortNativeCrashes(false).mapNotNull { crashFile ->
            try {
                val crashReport = delegate.getCrashReport(crashFile.path)
                if (crashReport != null) {
                    serializer.fromJson(crashReport, NativeCrashData::class.java).apply {
                        symbols = symbolService.symbolsForCurrentArch
                        cleanup?.invoke(crashFile)
                    }
                } else {
                    logger.trackInternalError(
                        type = InternalErrorType.NATIVE_CRASH_LOAD_FAIL,
                        throwable = FileNotFoundException("Failed to load crash report at ${crashFile.path}")
                    )
                    null
                }
            } catch (t: Throwable) {
                crashFile.delete()
                logger.trackInternalError(
                    type = InternalErrorType.NATIVE_CRASH_LOAD_FAIL,
                    throwable = RuntimeException(
                        "Failed to read native crash file {crashFilePath=" + crashFile.absolutePath + "}.",
                        t
                    )
                )
                null
            }
        }
        return nativeCrashes
    }

    private fun sortNativeCrashes(byOldest: Boolean): List<File> {
        val nativeCrashFiles: Array<File> = getNativeCrashFiles()
        val nativeCrashList: MutableList<File> = mutableListOf()

        nativeCrashList.addAll(nativeCrashFiles)
        val sorted: MutableMap<File, Long> = HashMap()
        runCatching {
            for (f in nativeCrashList) {
                sorted[f] = f.lastModified()
            }

            val comparator: Comparator<File> = if (byOldest) {
                Comparator { first: File, next: File ->
                    checkNotNull(sorted[first]?.compareTo(checkNotNull(sorted[next])))
                }
            } else {
                Comparator { first: File, next: File ->
                    checkNotNull(sorted[next]?.compareTo(checkNotNull(sorted[first])))
                }
            }
            return nativeCrashList.sortedWith(comparator)
        }

        return nativeCrashList
    }

    private fun getNativeCrashFiles(): Array<File> {
        val nativeCrashFilter =
            FilenameFilter { _: File?, name: String ->
                name.startsWith(
                    NATIVE_CRASH_FILE_PREFIX
                ) && name.endsWith(NATIVE_CRASH_FILE_SUFFIX)
            }
        return getNativeFiles(nativeCrashFilter)
    }

    private fun getNativeFiles(filter: FilenameFilter): Array<File> {
        val ndkDirs: List<File> = storageService.listFiles { file, name ->
            file.isDirectory && name == NATIVE_CRASH_FILE_FOLDER
        }

        val matchingFiles = ndkDirs.flatMap { dir ->
            dir.listFiles(filter)?.toList() ?: emptyList()
        }.toTypedArray()

        return matchingFiles
    }

    private fun deleteFiles(crashFile: File) {
        crashFile.delete()
    }

    private companion object {
        const val NATIVE_CRASH_FILE_PREFIX = "emb_ndk"
        const val NATIVE_CRASH_FILE_SUFFIX = ".crash"
    }
}

typealias CleanupFunction = (crashFile: File) -> Unit
