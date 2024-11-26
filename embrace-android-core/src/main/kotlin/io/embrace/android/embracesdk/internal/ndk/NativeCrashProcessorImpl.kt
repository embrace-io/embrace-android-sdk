package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.ndk.jni.JniDelegate
import io.embrace.android.embracesdk.internal.ndk.symbols.SymbolService
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.storage.StorageService
import java.io.File
import java.io.FileNotFoundException

class NativeCrashProcessorImpl(
    private val sharedObjectLoader: SharedObjectLoader,
    private val logger: EmbLogger,
    private val delegate: JniDelegate,
    private val serializer: PlatformSerializer,
    private val symbolService: SymbolService,
    private val storageService: StorageService,
) : NativeCrashProcessor {

    override fun getLatestNativeCrash(): NativeCrashData? {
        return getAllNativeCrashes().lastOrNull().also {
            deleteAllNativeCrashes()
        }
    }

    override fun getNativeCrashes(): List<NativeCrashData> = getAllNativeCrashes()

    override fun deleteAllNativeCrashes() {
        getNativeCrashFiles().forEach(File::delete)
    }

    private fun getAllNativeCrashes(): List<NativeCrashData> {
        if (!sharedObjectLoader.loaded.get()) {
            return emptyList()
        }
        val nativeCrashes = getNativeCrashFiles().mapNotNull { crashFile ->
            try {
                val crashReport = delegate.getCrashReport(crashFile.path)
                if (crashReport != null) {
                    serializer.fromJson(crashReport, NativeCrashData::class.java).apply {
                        symbols = symbolService.symbolsForCurrentArch
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

    private fun getNativeCrashFiles(): List<File> {
        val nativeCrashDir: File = storageService.getOrCreateNativeCrashDir()
        val files = nativeCrashDir.listFiles() ?: emptyArray()
        return files.filter { it.extension == "crash" }.sortedBy(File::lastModified)
    }
}
