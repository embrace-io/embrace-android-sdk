package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.ndk.jni.JniDelegate
import io.embrace.android.embracesdk.internal.ndk.symbols.SymbolService
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import java.io.FileNotFoundException

internal class NativeCrashProcessorImpl(
    private val sharedObjectLoader: SharedObjectLoader,
    private val logger: EmbLogger,
    private val repository: NdkServiceRepository,
    private val delegate: JniDelegate,
    private val serializer: PlatformSerializer,
    private val symbolService: SymbolService,
) : NativeCrashProcessor {

    override fun getLatestNativeCrash(): NativeCrashData? =
        getAllNativeCrashes(repository::deleteFiles).lastOrNull()

    override fun getNativeCrashes(): List<NativeCrashData> = getAllNativeCrashes()

    override fun deleteAllNativeCrashes() {
        getAllNativeCrashes(repository::deleteFiles)
    }

    private fun getAllNativeCrashes(
        cleanup: CleanupFunction? = null,
    ): List<NativeCrashData> {
        val nativeCrashes = mutableListOf<NativeCrashData>()
        if (sharedObjectLoader.loaded.get()) {
            val matchingFiles = repository.sortNativeCrashes(false)
            for (crashFile in matchingFiles) {
                try {
                    val path = crashFile.path
                    delegate.getCrashReport(path)?.let { crashRaw ->
                        val nativeCrash = serializer.fromJson(crashRaw, NativeCrashData::class.java)
                        nativeCrash.symbols = symbolService.symbolsForCurrentArch
                        nativeCrashes.add(nativeCrash)
                        cleanup?.invoke(crashFile)
                    } ?: {
                        logger.trackInternalError(
                            type = InternalErrorType.NATIVE_CRASH_LOAD_FAIL,
                            throwable = FileNotFoundException("Failed to load crash report at $path")
                        )
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
                }
            }
        }
        return nativeCrashes
    }
}
