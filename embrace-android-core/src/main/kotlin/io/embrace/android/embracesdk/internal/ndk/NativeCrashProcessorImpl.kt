package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.storage.FileStorageService
import io.embrace.android.embracesdk.internal.delivery.storage.FileStorageServiceImpl
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.ndk.jni.JniDelegate
import io.embrace.android.embracesdk.internal.ndk.symbols.SymbolService
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import java.io.File
import java.io.FileNotFoundException

class NativeCrashProcessorImpl(
    private val sharedObjectLoader: SharedObjectLoader,
    private val logger: EmbLogger,
    private val delegate: JniDelegate,
    private val serializer: PlatformSerializer,
    private val symbolService: SymbolService,
    private val outputDir: Lazy<File>,
    worker: PriorityWorker<StoredTelemetryMetadata>,
) : NativeCrashProcessor {

    private val fileStorageService: FileStorageService = FileStorageServiceImpl(
        outputDir,
        worker,
        logger,
    )

    override fun getLatestNativeCrash(): NativeCrashData? {
        return getAllNativeCrashes().lastOrNull().also {
            deleteAllNativeCrashes()
        }
    }

    override fun getNativeCrashes(): List<NativeCrashData> = getAllNativeCrashes()

    override fun deleteAllNativeCrashes() {
        fileStorageService.getStoredPayloads().forEach(fileStorageService::delete)
    }

    private fun getAllNativeCrashes(): List<NativeCrashData> {
        if (!sharedObjectLoader.loaded.get()) {
            return emptyList()
        }
        val files = fileStorageService.getStoredPayloads().map { File(outputDir.value, it.filename) }
        val nativeCrashes = files.mapNotNull { crashFile ->
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
}
