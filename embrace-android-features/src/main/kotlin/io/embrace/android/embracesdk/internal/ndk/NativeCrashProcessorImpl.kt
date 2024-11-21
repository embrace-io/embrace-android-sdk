package io.embrace.android.embracesdk.internal.ndk

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import io.embrace.android.embracesdk.internal.DeviceArchitecture
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.TypeUtils
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.ndk.jni.JniDelegate
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.payload.NativeCrashDataError
import io.embrace.android.embracesdk.internal.payload.NativeSymbols
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader

internal class NativeCrashProcessorImpl(
    private val context: Context,
    private val sharedObjectLoader: SharedObjectLoader,
    private val logger: EmbLogger,
    private val repository: NdkServiceRepository,
    private val delegate: JniDelegate,
    private val deviceArchitecture: DeviceArchitecture,
    private val serializer: PlatformSerializer,
) {

    val symbolsForCurrentArch by lazy {
        val nativeSymbols = getNativeSymbols()
        if (nativeSymbols != null) {
            val arch = deviceArchitecture.architecture
            return@lazy nativeSymbols.getSymbolByArchitecture(arch)
        }
        null
    }

    fun getLatestNativeCrash(): NativeCrashData? = getAllNativeCrashes(repository::deleteFiles).lastOrNull()

    fun getNativeCrashes(): List<NativeCrashData> = getAllNativeCrashes()

    fun deleteAllNativeCrashes() {
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
                        val errorFile = repository.errorFileForCrash(crashFile)?.apply {
                            getNativeCrashErrors(this).let { errors ->
                                nativeCrash.errors = errors
                            }
                        }
                        val mapFile = repository.mapFileForCrash(crashFile)?.apply {
                            nativeCrash.map = getMapFileContent(this)
                        }
                        nativeCrash.symbols = symbolsForCurrentArch?.toMap()

                        nativeCrashes.add(nativeCrash)
                        cleanup?.invoke(crashFile, errorFile, mapFile, nativeCrash)
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

    @SuppressLint("DiscouragedApi")
    private fun getNativeSymbols(): NativeSymbols? {
        val resources = context.resources
        val resourceId = resources.getIdentifier(KEY_NDK_SYMBOLS, "string", context.packageName)
        if (resourceId != 0) {
            try {
                val encodedSymbols: String = Base64.decode(
                    context.resources.getString(resourceId),
                    Base64.DEFAULT
                ).decodeToString()
                return serializer.fromJson(encodedSymbols, NativeSymbols::class.java)
            } catch (ex: Exception) {
                logger.trackInternalError(InternalErrorType.INVALID_NATIVE_SYMBOLS, ex)
            }
        }
        return null
    }

    /**
     * Find and parse a native error File to NativeCrashData Error List
     *
     * @return List of NativeCrashData error
     */
    private fun getNativeCrashErrors(errorFile: File?): List<NativeCrashDataError?>? {
        if (errorFile != null) {
            val absolutePath = errorFile.absolutePath
            val errorsRaw = delegate.getErrors(absolutePath)
            if (errorsRaw != null) {
                runCatching {
                    val type = TypeUtils.typedList(NativeCrashDataError::class)
                    return serializer.fromJson(errorsRaw, type)
                }
            }
        }
        return null
    }

    /**
     * Process map file for crash to read and return its content as String
     */
    private fun getMapFileContent(mapFile: File?): String? {
        if (mapFile != null) {
            val mapContents = readMapFile(mapFile)
            if (mapContents != null) {
                return mapContents
            }
        }
        return null
    }

    private fun readMapFile(mapFile: File): String? {
        try {
            FileInputStream(mapFile).use { fin ->
                BufferedReader(InputStreamReader(fin)).use { reader ->
                    val sb = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line).append("\n")
                    }
                    return sb.toString()
                }
            }
        } catch (e: IOException) {
            return null
        }
    }

    internal companion object {
        /**
         * The NDK symbols name that matches with the resource name injected by the plugin.
         */
        private const val KEY_NDK_SYMBOLS = "emb_ndk_symbols"
    }
}
