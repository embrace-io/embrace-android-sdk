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
        if (!sharedObjectLoader.loaded.get()) {
            return emptyList()
        }
        val nativeCrashes = repository.sortNativeCrashes(false).mapNotNull { crashFile ->
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
}
