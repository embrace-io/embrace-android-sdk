package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.storage.FileStorageService
import io.embrace.android.embracesdk.internal.delivery.storage.FileStorageServiceImpl
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.jni.JniDelegate
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
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

    private val errorHandler: ErrorHandler = args.errorHandler
    private val serializer: PlatformSerializer = args.serializer
    private val fileStorageService: FileStorageService = FileStorageServiceImpl(
        outputDir,
        worker,
        errorHandler,
        args.clock,
    )

    /**
     * The native crashes on disk, lazily-indexed, each with the stored metadata file it was read
     * from, by native crash ID. Indexing once is safe because no new crash files will appear
     * during the lifetime of the process given they are only written after a crash, which means the
     * process (and this map) is dead anyway.
     *
     * Not synchronized, as every caller runs on the same worker thread.
     */
    private val crashes: MutableMap<String, IndexedCrash> by lazy(::indexNativeCrashes)

    /**
     * IDs of the indexed crashes that have been deleted, which have already been handled and so must not be sent again.
     */
    private val deletedCrashIds = mutableSetOf<String>()

    override fun getNativeCrashes(): List<NativeCrashData> {
        if (!sharedObjectLoader.loaded.get()) {
            return emptyList()
        }
        return crashes.values.map(IndexedCrash::summary)
    }

    override fun loadNativeCrash(nativeCrash: NativeCrashData): NativeCrashData? {
        if (!sharedObjectLoader.loaded.get()) {
            return nativeCrash
        }
        val indexed = crashes[nativeCrash.nativeCrashId]
            ?: return nativeCrash.takeUnless { it.nativeCrashId in deletedCrashIds }
        return readCrash(indexed.metadata, phase = "sending")?.copy(symbols = symbolMap) ?: indexed.summary
    }

    override fun deleteAllNativeCrashes() {
        if (sharedObjectLoader.loaded.get()) {
            deletedCrashIds.addAll(crashes.keys)
            crashes.clear()
        }
        fileStorageService.getStoredPayloads().forEach(fileStorageService::delete)
    }

    override fun deleteNativeCrash(nativeCrash: NativeCrashData) {
        if (!sharedObjectLoader.loaded.get()) {
            return
        }
        crashes.remove(nativeCrash.nativeCrashId)?.let {
            deletedCrashIds.add(nativeCrash.nativeCrashId)
            fileStorageService.delete(it.metadata)
        }
    }

    private fun indexNativeCrashes(): MutableMap<String, IndexedCrash> {
        val nativeCrashes = LinkedHashMap<String, IndexedCrash>()
        fileStorageService.getStoredPayloads().forEach { metadata ->
            readCrash(metadata, phase = "indexing")?.let { nativeCrash ->
                val summary = nativeCrash.copy(crash = null, symbols = null)
                nativeCrashes[summary.nativeCrashId] = IndexedCrash(summary, metadata)
            }
        }
        return nativeCrashes
    }

    /**
     * Reads the crash stored in the file for [metadata], or deletes the file and returns null if it can't be read.
     * [phase] says whether the crash was being indexed or sent, which tells apart a crash that was dropped from
     * one that was sent without its report.
     */
    private fun readCrash(metadata: StoredTelemetryMetadata, phase: String): NativeCrashData? {
        val crashFile = File(outputDir.value, metadata.filename)
        try {
            val crashReport = delegate.getCrashReport(crashFile.path)
            if (crashReport != null) {
                return serializer.fromJson(crashReport, NativeCrashData.serializer())
            }
            // A file that can't be read now never will be (e.g. truncated mid-write), so delete it
            // rather than failing to load it on every launch.
            crashFile.delete()
            errorHandler.trackSdkError(
                type = SdkErrorType.NativeCrashLoadFail,
                throwable = FileNotFoundException("Failed to load crash report at ${crashFile.path} while $phase"),
            )
        } catch (t: Throwable) {
            crashFile.delete()
            errorHandler.trackSdkError(
                type = SdkErrorType.NativeCrashLoadFail,
                throwable = RuntimeException(
                    "Failed to read native crash file while $phase {crashFilePath=" + crashFile.absolutePath + "}.",
                    t,
                ),
            )
        }
        return null
    }

    /**
     * A summary of a native crash, without its report or symbols, and the stored metadata file that defines its identity.
     */
    private class IndexedCrash(
        val summary: NativeCrashData,
        val metadata: StoredTelemetryMetadata,
    )
}
