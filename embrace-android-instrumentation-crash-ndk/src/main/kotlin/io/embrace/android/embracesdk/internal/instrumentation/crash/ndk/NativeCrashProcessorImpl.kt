package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.storage.FileStorageService
import io.embrace.android.embracesdk.internal.delivery.storage.FileStorageServiceImpl
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.jni.JniDelegate
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
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

    private val logger: InternalLogger = args.logger
    private val serializer: PlatformSerializer = args.serializer
    private val fileStorageService: FileStorageService = FileStorageServiceImpl(
        outputDir,
        worker,
        logger,
        args.clock,
    )

    /**
     * The native crashes on disk, lazily-loaded, each with the stored metadata file it was loaded
     * using, by native crash ID. Loading once is safe because no new crash files will appear
     * during the lifetime of the process given they are only written after a crash, which means the
     * process (and this map) is dead anyway.
     *
     * Not synchronized, as every caller runs on the same worker thread.
     */
    private val crashes: MutableMap<String, LoadedCrash> by lazy(::loadAllNativeCrashes)

    override fun getLatestNativeCrash(): NativeCrashData? {
        return getNativeCrashes().lastOrNull().also {
            deleteAllNativeCrashes()
        }
    }

    override fun getNativeCrashes(): List<NativeCrashData> {
        if (!sharedObjectLoader.loaded.get()) {
            return emptyList()
        }
        return crashes.values.map(LoadedCrash::data)
    }

    override fun deleteAllNativeCrashes() {
        if (sharedObjectLoader.loaded.get()) {
            crashes.clear()
        }
        fileStorageService.getStoredPayloads().forEach(fileStorageService::delete)
    }

    override fun deleteNativeCrash(nativeCrash: NativeCrashData) {
        if (!sharedObjectLoader.loaded.get()) {
            return
        }
        crashes.remove(nativeCrash.nativeCrashId)?.let { fileStorageService.delete(it.metadata) }
    }

    private fun loadAllNativeCrashes(): MutableMap<String, LoadedCrash> {
        val nativeCrashes = LinkedHashMap<String, LoadedCrash>()
        fileStorageService.getStoredPayloads().forEach { metadata ->
            val crashFile = File(outputDir.value, metadata.filename)
            try {
                val crashReport = delegate.getCrashReport(crashFile.path)
                if (crashReport != null) {
                    val nativeCrash = serializer
                        .fromJson(crashReport, NativeCrashData.serializer())
                        .copy(symbols = symbolMap)
                    nativeCrashes[nativeCrash.nativeCrashId] = LoadedCrash(nativeCrash, metadata)
                } else {
                    logger.trackInternalError(
                        type = InternalErrorType.NativeCrashLoadFail,
                        throwable = FileNotFoundException("Failed to load crash report at ${crashFile.path}"),
                    )
                }
            } catch (t: Throwable) {
                crashFile.delete()
                logger.trackInternalError(
                    type = InternalErrorType.NativeCrashLoadFail,
                    throwable = RuntimeException(
                        "Failed to read native crash file {crashFilePath=" + crashFile.absolutePath + "}.",
                        t,
                    ),
                )
            }
        }
        return nativeCrashes
    }

    /**
     * A native crash and the stored metadata file that defines its identity.
     */
    private class LoadedCrash(
        val data: NativeCrashData,
        val metadata: StoredTelemetryMetadata,
    )
}
