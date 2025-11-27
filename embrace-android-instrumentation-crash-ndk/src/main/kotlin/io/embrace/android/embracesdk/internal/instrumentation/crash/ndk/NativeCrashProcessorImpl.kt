package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.storage.FileStorageService
import io.embrace.android.embracesdk.internal.delivery.storage.FileStorageServiceImpl
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.jni.JniDelegate
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import java.io.File
import java.io.FileNotFoundException

internal class NativeCrashProcessorImpl(
    args: InstrumentationArgs,
    private val sharedObjectLoader: SharedObjectLoader,
    private val delegate: JniDelegate,
    private val symbolMap: Map<String, String>?,
    private val outputDir: Lazy<File>,
    worker: PriorityWorker<StoredTelemetryMetadata>,
) : NativeCrashProcessor {

    private val logger: EmbLogger = args.logger
    private val serializer: PlatformSerializer = args.serializer
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
                        this.symbols = symbolMap
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
