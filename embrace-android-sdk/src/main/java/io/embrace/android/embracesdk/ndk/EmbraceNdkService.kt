package io.embrace.android.embracesdk.ndk

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.EmbraceEvent
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.comms.api.ApiClient
import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.ApkToolsConfig
import io.embrace.android.embracesdk.internal.DeviceArchitecture
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.crash.CrashFileMarker
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.utils.Uuid.getEmbUuid
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.Event
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NativeCrashData
import io.embrace.android.embracesdk.payload.NativeCrashDataError
import io.embrace.android.embracesdk.payload.NativeCrashMetadata
import io.embrace.android.embracesdk.payload.NativeSymbols
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateListener
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import io.embrace.android.embracesdk.storage.StorageManager
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FilenameFilter
import java.io.IOException
import java.io.InputStreamReader
import java.util.LinkedList
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService

internal class EmbraceNdkService(
    private val context: Context,
    private val storageManager: StorageManager,
    private val metadataService: MetadataService,
    processStateService: ProcessStateService,
    private val configService: ConfigService,
    private val deliveryService: DeliveryService,
    private val userService: UserService,
    private val sessionProperties: EmbraceSessionProperties,
    appFramework: AppFramework,
    private val sharedObjectLoader: SharedObjectLoader,
    private val logger: InternalEmbraceLogger,
    private val repository: EmbraceNdkServiceRepository,
    private val delegate: NdkServiceDelegate.NdkDelegate,
    private val cleanCacheExecutorService: ExecutorService,
    private val ndkStartupExecutorService: ExecutorService,
    /**
     * The device architecture.
     */
    private val deviceArchitecture: DeviceArchitecture,
    private val serializer: EmbraceSerializer
) : NdkService, ProcessStateListener {
    /**
     * Synchronization lock.
     */
    private val lock = Any()

    /**
     * Whether or not the NDK has been installed.
     */
    private var isInstalled = false
    private var unityCrashId: String? = null
    private val symbolsForArch: Lazy<Map<String, String>?>

    init {
        this.symbolsForArch = lazy {
            val nativeSymbols = getNativeSymbols()
            if (nativeSymbols != null) {
                val arch = deviceArchitecture.architecture
                return@lazy nativeSymbols.getSymbolByArchitecture(arch)
            }
            null
        }
        if (configService.autoDataCaptureBehavior.isNdkEnabled()) {
            processStateService.addListener(this)
            if (appFramework == AppFramework.UNITY) {
                unityCrashId = getEmbUuid()
            }
            logger.logDeveloper("EmbraceNDKService", "NDK enabled - starting service installation.")
            startNdk()
            cleanOldCrashFiles()
        } else {
            logger.logDeveloper("EmbraceNDKService", "NDK disabled.")
        }
    }

    override fun testCrash(isCpp: Boolean) {
        if (isCpp) {
            testCrashCpp()
        } else {
            testCrashC()
        }
    }

    override fun updateSessionId(newSessionId: String) {
        logger.logDeveloper("EmbraceNDKService", "NDK update (session ID): $newSessionId")
        if (isInstalled) {
            delegate._updateSessionId(newSessionId)
        }
    }

    override fun onSessionPropertiesUpdate(properties: Map<String, String>) {
        logger.logDeveloper("EmbraceNDKService", "NDK update: (session properties): $properties")
        if (isInstalled) {
            updateDeviceMetaData()
        }
    }

    override fun onUserInfoUpdate() {
        logger.logDeveloper("EmbraceNDKService", "NDK update (user)")
        if (isInstalled) {
            updateDeviceMetaData()
        }
    }

    override fun getUnityCrashId(): String? {
        return unityCrashId
    }

    override fun onBackground(timestamp: Long) {
        synchronized(lock) {
            if (isInstalled) {
                updateAppState(APPLICATION_STATE_BACKGROUND)
            }
        }
    }

    override fun onForeground(coldStart: Boolean, startupTime: Long, timestamp: Long) {
        synchronized(lock) {
            if (isInstalled) {
                updateAppState(APPLICATION_STATE_ACTIVE)
            }
        }
    }

    private fun startNdk() {
        try {
            if (sharedObjectLoader.loadEmbraceNative()) {
                installSignals()
                createCrashReportDirectory()
                val handler = Handler(checkNotNull(Looper.myLooper()))
                handler.postDelayed(
                    Runnable(::checkSignalHandlersOverwritten),
                    HANDLER_CHECK_DELAY_MS.toLong()
                )
                logger.logInfo("NDK library successfully loaded")
            } else {
                logger.logDeveloper(
                    "EmbraceNDKService",
                    "Failed to load embrace library - probable unsatisfied linkage."
                )
            }
        } catch (ex: Exception) {
            logger.logError("Failed to load NDK library", ex)
        }
    }

    private fun checkSignalHandlersOverwritten() {
        if (configService.autoDataCaptureBehavior.isSigHandlerDetectionEnabled()) {
            val culprit = delegate._checkForOverwrittenHandlers()
            if (culprit != null) {
                if (shouldIgnoreOverriddenHandler(culprit)) {
                    return
                }
                val errMsg = """
                    Embrace detected that another signal handler has replaced our signal handler.
                    This may lead to unexpected behaviour and lost NDK crashes.
                    We will attempt to reinstall our signal handler but please consider disabling
                    other signal handlers if you observed unexpected behaviour.
                    If you believe this is a false positive, please contact support@embrace.io.
                    Handler origin: $culprit
                """.trimIndent()
                val exc = RuntimeException(errMsg)
                exc.stackTrace = arrayOfNulls(0)
                logger.logWarningWithException(errMsg, exc, false)
                delegate._reinstallSignalHandlers()
            }
        }
    }

    /**
     * Contains a list of SO files which are known to install signal handlers that do not
     * interfere with crash detection. This list will probably expand over time.
     *
     * @param culprit the culprit SO file as identified by dladdr
     * @return true if we can safely ignore
     */
    private fun shouldIgnoreOverriddenHandler(culprit: String): Boolean {
        val allowList = listOf("libwebviewchromium.so")
        return allowList.any(culprit::contains)
    }

    private fun createCrashReportDirectory() {
        val directoryFile = storageManager.getFile(NATIVE_CRASH_FILE_FOLDER, false)
        if (directoryFile.exists()) {
            return
        }
        if (!directoryFile.mkdirs()) {
            logger.logError("Failed to create crash report directory {crashDirPath=" + directoryFile.absolutePath + "}")
        }
    }

    private fun installSignals() {
        val reportBasePath =
            storageManager.getFile(NATIVE_CRASH_FILE_FOLDER, false).absolutePath
        val markerFilePath =
            storageManager.getFile(CrashFileMarker.CRASH_MARKER_FILE_NAME, false).absolutePath

        logger.logDeveloper("EmbraceNDKService", "Creating report path at $reportBasePath")

        // Assign the native crash id to the unity crash id. Then when a unity crash occurs, the
        // Embrace crash service will set the unity crash id to the java crash.
        val nativeCrashId: String = unityCrashId ?: getEmbUuid()
        val is32bit = deviceArchitecture.is32BitDevice
        logger.logDeveloper(
            "EmbraceNDKService",
            "Installing signal handlers. 32bit=$is32bit, crashId=$nativeCrashId"
        )
        val initialMetaData = serializer.toJson(
            NativeCrashMetadata(
                metadataService.getLightweightAppInfo(),
                metadataService.getLightweightDeviceInfo(),
                userService.getUserInfo(),
                sessionProperties.get().toMap()
            )
        )
        delegate._installSignalHandlers(
            reportBasePath,
            markerFilePath,
            initialMetaData,
            "null",
            metadataService.getAppState(),
            nativeCrashId,
            Build.VERSION.SDK_INT,
            is32bit,
            ApkToolsConfig.IS_DEVELOPER_LOGGING_ENABLED
        )
        updateDeviceMetaData()
        isInstalled = true
    }

    /**
     * Find and parse a native error File to NativeCrashData Error List
     *
     * @return List of NativeCrashData error
     */
    private fun getNativeCrashErrors(
        nativeCrash: NativeCrashData,
        errorFile: File?
    ): List<NativeCrashDataError?>? {
        if (errorFile != null) {
            val absolutePath = errorFile.absolutePath
            logger.logDeveloper("EmbraceNDKService", "Processing error file at $absolutePath")
            val errorsRaw = delegate._getErrors(absolutePath)
            if (errorsRaw != null) {
                try {
                    return serializer.fromJsonWithTypeToken<ArrayList<NativeCrashDataError?>>(
                        errorsRaw
                    )
                } catch (e: Exception) {
                    logger.logError(
                        "Failed to parse native crash error file {crashId=" + nativeCrash.nativeCrashId +
                            ", errorFilePath=" + absolutePath + "}"
                    )
                }
            } else {
                logger.logDeveloper("EmbraceNDKService", "Failed to load errorsRaw.")
            }
        } else {
            logger.logDeveloper("EmbraceNDKService", "Failed to find error file for crash.")
        }
        return null
    }

    /**
     * Process map file for crash to read and return its content as String
     */
    private fun getMapFileContent(mapFile: File?): String? {
        if (mapFile != null) {
            logger.logDeveloper(
                "EmbraceNDKService",
                "Processing map file at " + mapFile.absolutePath
            )
            val mapContents = readMapFile(mapFile)
            if (mapContents != null) {
                return mapContents
            } else {
                logger.logDeveloper("EmbraceNDKService", "Failed to load mapContents.")
            }
        } else {
            logger.logDeveloper("EmbraceNDKService", "Failed to find map file for crash.")
        }
        return null
    }

    /**
     * Check if a native crash file exists. Also checks for the symbols file in the build dir.
     * If so, attempt to send an event message and call [SessionService] to update the crash
     * report id in the appropriate pending session.
     *
     * @return Crash data, if a native crash file was found
     */
    override fun checkForNativeCrash(): NativeCrashData? {
        logger.logDeveloper("EmbraceNDKService", "Processing native crash check runnable.")
        var nativeCrash: NativeCrashData? = null
        val matchingFiles = repository.sortNativeCrashes(false)
        logger.logDeveloper("EmbraceNDKService", "Found " + matchingFiles.size + " native crashes.")
        for (crashFile in matchingFiles) {
            try {
                val path = crashFile.path
                val crashRaw = delegate._getCrashReport(path)
                logger.logDeveloper("EmbraceNDKService", "Processing native crash at $path")
                if (crashRaw != null) {
                    nativeCrash = serializer.fromJson(crashRaw, NativeCrashData::class.java)
                } else {
                    logger.logError("Failed to load crash report at $path")
                }
                val errorFile = repository.errorFileForCrash(crashFile)
                if (nativeCrash != null) {
                    val errors = getNativeCrashErrors(nativeCrash, errorFile)
                    if (errors != null) {
                        nativeCrash.errors = errors
                    } else {
                        logger.logDeveloper(
                            "EmbraceNDKService",
                            "Failed to find error file for crash."
                        )
                    }
                } else {
                    logger.logDeveloper("EmbraceNDKService", "Failed to find error file for crash.")
                }
                val mapFile = repository.mapFileForCrash(crashFile)
                if (mapFile != null && nativeCrash != null) {
                    nativeCrash.map = getMapFileContent(mapFile)
                } else {
                    logger.logDeveloper("EmbraceNDKService", "Failed to find map file for crash.")
                }

                // Retrieve deobfuscated symbols
                if (nativeCrash != null) {
                    val symbols = getSymbolsForCurrentArch()
                    if (symbols == null) {
                        logger.logError("Failed to find symbols for native crash - stacktraces will not symbolicate correctly.")
                    } else {
                        nativeCrash.symbols = symbols.toMap()
                        logger.logDeveloper("EmbraceNDKService", "Added symbols for native crash")
                    }
                    sendNativeCrash(nativeCrash)
                }
                repository.deleteFiles(crashFile, errorFile, mapFile, nativeCrash)
            } catch (ex: Exception) {
                crashFile.delete()
                logger.logError(
                    "Failed to read native crash file {crashFilePath=" + crashFile.absolutePath + "}.",
                    ex,
                    true
                )
            }
        }
        return nativeCrash
    }

    override fun getSymbolsForCurrentArch(): Map<String, String>? {
        return symbolsForArch.value
    }

    @SuppressLint("DiscouragedApi")
    private fun getNativeSymbols(): NativeSymbols? {
        val resources = context.resources
        val resourceId = resources.getIdentifier(KEY_NDK_SYMBOLS, "string", context.packageName)
        if (resourceId != 0) {
            try {
                val encodedSymbols: String = Base64.decode(context.resources.getString(resourceId), Base64.DEFAULT).decodeToString()
                return serializer.fromJson(encodedSymbols, NativeSymbols::class.java)
            } catch (ex: Exception) {
                logger.logError(
                    String.format(
                        Locale.getDefault(),
                        "Failed to decode symbols from resources {resourceId=%d}.",
                        resourceId
                    ),
                    ex
                )
            }
        } else {
            logger.logError(
                String.format(
                    Locale.getDefault(),
                    "Failed to find symbols in resources {resourceId=%d}.",
                    resourceId
                )
            )
        }
        return null
    }

    private fun getNativeFiles(filter: FilenameFilter): Array<File> {
        val ndkDirs: List<File> = storageManager.listFiles { file, name ->
            file.isDirectory && name == NATIVE_CRASH_FILE_FOLDER
        }

        val matchingFiles = ndkDirs.flatMap { dir ->
            dir.listFiles(filter)?.toList() ?: emptyList()
        }.toTypedArray()

        return matchingFiles
    }

    private fun getNativeErrorFiles(): Array<File> {
        val nativeCrashFilter = FilenameFilter { _: File?, name: String ->
            name.startsWith(
                NATIVE_CRASH_FILE_PREFIX
            ) && name.endsWith(NATIVE_CRASH_ERROR_FILE_SUFFIX)
        }
        return getNativeFiles(nativeCrashFilter)
    }

    private fun getNativeMapFiles(): Array<File> {
        val nativeCrashFilter = FilenameFilter { _: File?, name: String ->
            name.startsWith(
                NATIVE_CRASH_FILE_PREFIX
            ) && name.endsWith(NATIVE_CRASH_MAP_FILE_SUFFIX)
        }
        return getNativeFiles(nativeCrashFilter)
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

    private fun cleanOldCrashFiles() {
        cleanCacheExecutorService.submit(
            Callable<Any?> {
                logger.logDeveloper("EmbraceNDKService", "Processing clean of old crash files.")
                val sortedFiles = repository.sortNativeCrashes(true)
                val deleteCount = sortedFiles.size - MAX_NATIVE_CRASH_FILES_ALLOWED
                if (deleteCount > 0) {
                    val files = LinkedList(sortedFiles)
                    try {
                        for (i in 0 until deleteCount) {
                            val removed = files[i]
                            if (files[i].delete()) {
                                logger.logDebug("Native crash file " + removed.name + " removed from cache")
                            }
                        }
                    } catch (ex: Exception) {
                        logger.logError("Failed to delete native crash from cache.", ex)
                    }
                }

                // delete error files that don't have matching crash files
                val errorFiles = getNativeErrorFiles()
                for (errorFile in errorFiles) {
                    if (hasNativeCrashFile(errorFile)) {
                        logger.logDeveloper(
                            "EmbraceNDKService",
                            "Skipping error file as it has a matching crash file " + errorFile.absolutePath
                        )
                        continue
                    }
                    errorFile.delete()
                    logger.logDeveloper(
                        "EmbraceNDKService",
                        "Deleting error file as it has no matching crash file " + errorFile.absolutePath
                    )
                }

                // delete map files that don't have matching crash files
                val mapFiles = getNativeMapFiles()
                for (mapFile in mapFiles) {
                    if (hasNativeCrashFile(mapFile)) {
                        logger.logDeveloper(
                            "EmbraceNDKService",
                            "Skipping map file as it has a matching crash file " + mapFile.absolutePath
                        )
                        continue
                    }
                    mapFile.delete()
                    logger.logDeveloper(
                        "EmbraceNDKService",
                        "Deleting map file as it has no matching crash file " + mapFile.absolutePath
                    )
                }
                null
            }
        )
    }

    private fun hasNativeCrashFile(file: File): Boolean {
        val filename = file.absolutePath
        if (!filename.contains(".")) {
            return false
        }
        val crashFilename =
            filename.substring(0, filename.lastIndexOf('.')) + NATIVE_CRASH_FILE_SUFFIX
        val crashFile = File(crashFilename)
        return crashFile.exists()
    }

    private fun sendNativeCrash(nativeCrash: NativeCrashData) {
        logger.logDeveloper("EmbraceNDKService", "Constructing EventMessage from native crash.")
        val metadata = nativeCrash.metadata

        val nativeCrashEvent = Event(
            CRASH_REPORT_EVENT_NAME,
            null,
            getEmbUuid(),
            nativeCrash.sessionId,
            EmbraceEvent.Type.CRASH,
            nativeCrash.timestamp,
            null,
            false,
            null,
            nativeCrash.appState,
            null,
            metadata?.sessionProperties,
            null,
            null,
            null,
            null,
            null
        )
        val nativeCrashMessageEvent = EventMessage(
            nativeCrashEvent,
            null,
            metadata?.deviceInfo,
            metadata?.appInfo,
            metadata?.userInfo,
            null,
            null,
            ApiClient.MESSAGE_VERSION,
            nativeCrash.getCrash()
        )
        try {
            logger.logDeveloper(
                "EmbraceNDKService",
                "About to send EventMessage from native crash."
            )
            deliveryService.sendCrash(nativeCrashMessageEvent, false)
            logger.logDeveloper(
                "EmbraceNDKService",
                "Finished send attempt for EventMessage from native crash."
            )
        } catch (ex: Exception) {
            logger.logError(
                "Failed to report native crash to the api {sessionId=" + nativeCrash.sessionId +
                    ", crashId=" + nativeCrash.nativeCrashId,
                ex
            )
        }
    }

    private fun updateAppState(newAppState: String) {
        logger.logDeveloper("EmbraceNDKService", "NDK update (app state): $newAppState")
        delegate._updateAppState(newAppState)
    }

    /**
     * Compute NDK metadata on a background thread.
     */
    private fun updateDeviceMetaData() {
        ndkStartupExecutorService.submit(
            Callable<Any?> {
                logger.logDeveloper("EmbraceNDKService", "Processing NDK metadata update on bg thread.")
                var newDeviceMetaData = getMetaData(true)
                logger.logDeveloper("EmbraceNDKService", "NDK update (metadata): $newDeviceMetaData")
                if (newDeviceMetaData.length >= EMB_DEVICE_META_DATA_SIZE) {
                    logger.logDebug("Removing session properties from metadata to avoid exceeding size limitation for NDK metadata.")
                    newDeviceMetaData = getMetaData(false)
                }
                delegate._updateMetaData(newDeviceMetaData)
                null
            }
        )
    }

    private fun getMetaData(includeSessionProperties: Boolean): String {
        return serializer.toJson(
            NativeCrashMetadata(
                metadataService.getAppInfo(),
                metadataService.getDeviceInfo(),
                userService.getUserInfo(),
                if (includeSessionProperties) sessionProperties.get().toMap() else null
            )
        )
    }

    @Suppress("UnusedPrivateMember")
    private fun uninstallSignals() {
        delegate._uninstallSignals()
    }

    private fun testCrashC() {
        delegate._testNativeCrash_C()
    }

    private fun testCrashCpp() {
        delegate._testNativeCrash_CPP()
    }

    companion object {
        /**
         * Signals to the API that the application was in the foreground.
         */
        private const val APPLICATION_STATE_ACTIVE = "active"

        /**
         * Signals to the API that the application was in the background.
         */
        private const val APPLICATION_STATE_BACKGROUND = "background"

        /**
         * The NDK symbols name that matches with the resource name injected by the plugin.
         */
        private const val KEY_NDK_SYMBOLS = "emb_ndk_symbols"
        private const val CRASH_REPORT_EVENT_NAME = "_crash_report"
        private const val NATIVE_CRASH_FILE_PREFIX = "emb_ndk"
        private const val NATIVE_CRASH_FILE_SUFFIX = ".crash"
        private const val NATIVE_CRASH_ERROR_FILE_SUFFIX = ".error"
        private const val NATIVE_CRASH_MAP_FILE_SUFFIX = ".map"
        private const val NATIVE_CRASH_FILE_FOLDER = "ndk"
        private const val MAX_NATIVE_CRASH_FILES_ALLOWED = 4
        private const val EMB_DEVICE_META_DATA_SIZE = 2048
        private const val HANDLER_CHECK_DELAY_MS = 5000
    }
}
