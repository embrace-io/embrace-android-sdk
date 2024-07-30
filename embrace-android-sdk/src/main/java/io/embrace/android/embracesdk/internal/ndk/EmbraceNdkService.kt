package io.embrace.android.embracesdk.internal.ndk

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.squareup.moshi.Types
import io.embrace.android.embracesdk.internal.DeviceArchitecture
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.comms.api.ApiClient
import io.embrace.android.embracesdk.internal.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.crash.CrashFileMarkerImpl
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.Event
import io.embrace.android.embracesdk.internal.payload.EventMessage
import io.embrace.android.embracesdk.internal.payload.EventType
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.payload.NativeCrashDataError
import io.embrace.android.embracesdk.internal.payload.NativeCrashMetadata
import io.embrace.android.embracesdk.internal.payload.NativeSymbols
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateListener
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.internal.session.properties.EmbraceSessionProperties
import io.embrace.android.embracesdk.internal.storage.NATIVE_CRASH_FILE_FOLDER
import io.embrace.android.embracesdk.internal.storage.StorageService
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FilenameFilter
import java.io.IOException
import java.io.InputStreamReader
import java.util.LinkedList
import java.util.Locale

internal class EmbraceNdkService(
    private val context: Context,
    private val storageService: StorageService,
    private val metadataService: MetadataService,
    processStateService: ProcessStateService,
    private val configService: ConfigService,
    private val deliveryService: DeliveryService,
    private val userService: UserService,
    private val preferencesService: PreferencesService,
    private val sessionProperties: EmbraceSessionProperties,
    private val sharedObjectLoader: SharedObjectLoader,
    private val logger: EmbLogger,
    private val repository: EmbraceNdkServiceRepository,
    private val delegate: NdkServiceDelegate.NdkDelegate,
    private val backgroundWorker: BackgroundWorker,
    highPriorityWorker: BackgroundWorker,
    /**
     * The device architecture.
     */
    private val deviceArchitecture: DeviceArchitecture,
    private val serializer: PlatformSerializer
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
            // Workaround to load native symbols synchronously so the main thread won't be blocked by the background
            // thread doing this when the native ANR monitoring tries to load this
            // Remove this once the native ANR initialization is done on the background thread too.
            sharedObjectLoader.loadEmbraceNative()
            if (configService.sdkModeBehavior.isServiceInitDeferred()) {
                highPriorityWorker.submit {
                    initializeService(processStateService, configService.appFramework)
                }
            } else {
                initializeService(processStateService, configService.appFramework)
            }
        }
    }

    override fun updateSessionId(newSessionId: String) {
        if (isInstalled) {
            delegate._updateSessionId(newSessionId)
        }
    }

    override fun onSessionPropertiesUpdate(properties: Map<String, String>) {
        if (isInstalled) {
            backgroundWorker.submit {
                updateDeviceMetaData()
            }
        }
    }

    override fun onUserInfoUpdate() {
        if (isInstalled) {
            backgroundWorker.submit {
                updateDeviceMetaData()
            }
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

    override fun onForeground(coldStart: Boolean, timestamp: Long) {
        synchronized(lock) {
            if (isInstalled) {
                updateAppState(APPLICATION_STATE_FOREGROUND)
            }
        }
    }

    private fun startNativeCrashMonitoring() {
        try {
            if (sharedObjectLoader.loadEmbraceNative()) {
                installSignals()
                createCrashReportDirectory()
                val handler = Handler(checkNotNull(Looper.myLooper()))
                handler.postDelayed(
                    Runnable(::checkSignalHandlersOverwritten),
                    HANDLER_CHECK_DELAY_MS.toLong()
                )
            }
        } catch (ex: Exception) {
            logger.logError("Failed to start native crash monitoring", ex)
            logger.trackInternalError(InternalErrorType.NATIVE_HANDLER_INSTALL_FAIL, ex)
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
                logger.logWarning(errMsg, exc)
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
        val directoryFile = storageService.getNativeCrashDir()
        if (directoryFile.exists()) {
            return
        }
        if (!directoryFile.mkdirs()) {
            logger.logError("Failed to create crash report directory {crashDirPath=" + directoryFile.absolutePath + "}")
        }
    }

    private fun installSignals() {
        val reportBasePath = storageService.getNativeCrashDir().absolutePath
        val markerFilePath = storageService.getFileForWrite(
            CrashFileMarkerImpl.CRASH_MARKER_FILE_NAME
        ).absolutePath

        // Assign the native crash id to the unity crash id. Then when a unity crash occurs, the
        // Embrace crash service will set the unity crash id to the java crash.
        val nativeCrashId: String = unityCrashId ?: Uuid.getEmbUuid()
        val is32bit = deviceArchitecture.is32BitDevice
        val initialMetaData = Systrace.traceSynchronous("init-native-crash-metadata") {
            serializer.toJson(
                NativeCrashMetadata(
                    metadataService.getLightweightAppInfo(),
                    metadataService.getLightweightDeviceInfo(),
                    userService.getUserInfo(),
                    sessionProperties.get().toMap()
                )
            )
        }
        Systrace.traceSynchronous("native-install-handlers") {
            delegate._installSignalHandlers(
                reportBasePath,
                markerFilePath,
                initialMetaData,
                "null",
                metadataService.getAppState(),
                nativeCrashId,
                Build.VERSION.SDK_INT,
                is32bit,
                false
            )
        }
        Systrace.traceSynchronous("update-metadata") { updateDeviceMetaData() }
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
            val errorsRaw = delegate._getErrors(absolutePath)
            if (errorsRaw != null) {
                try {
                    val type = Types.newParameterizedType(List::class.java, NativeCrashDataError::class.java)
                    return serializer.fromJson(errorsRaw, type)
                } catch (e: Exception) {
                    logger.logError(
                        "Failed to parse native crash error file {crashId=" + nativeCrash.nativeCrashId +
                            ", errorFilePath=" + absolutePath + "}"
                    )
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

    /**
     * Check if a native crash file exists. Also checks for the symbols file in the build dir.
     * If so, attempt to send an event message and call [SessionService] to update the crash
     * report id in the appropriate pending session.
     *
     * @return Crash data, if a native crash file was found
     */
    override fun getAndSendNativeCrash(): NativeCrashData? {
        return getNativeCrash()?.apply { sendNativeCrash(this) }
    }

    override fun getNativeCrash(): NativeCrashData? {
        var nativeCrash: NativeCrashData? = null
        val matchingFiles = repository.sortNativeCrashes(false)
        for (crashFile in matchingFiles) {
            try {
                val path = crashFile.path
                val crashRaw = delegate._getCrashReport(path)
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
                    }
                }
                val mapFile = repository.mapFileForCrash(crashFile)
                if (mapFile != null && nativeCrash != null) {
                    nativeCrash.map = getMapFileContent(mapFile)
                }

                // Retrieve deobfuscated symbols
                if (nativeCrash != null) {
                    val symbols = getSymbolsForCurrentArch()
                    if (symbols == null) {
                        logger.logError("Failed to find symbols for native crash - stacktraces will not symbolicate correctly.")
                    } else {
                        nativeCrash.symbols = symbols.toMap()
                    }
                }
                repository.deleteFiles(crashFile, errorFile, mapFile, nativeCrash)
            } catch (ex: Exception) {
                crashFile.delete()
                logger.logError(
                    "Failed to read native crash file {crashFilePath=" + crashFile.absolutePath + "}.",
                    ex
                )
                logger.trackInternalError(InternalErrorType.NATIVE_CRASH_LOAD_FAIL, ex)
            }
        }
        return nativeCrash
    }

    override fun getSymbolsForCurrentArch(): Map<String, String>? {
        return symbolsForArch.value
    }

    private fun initializeService(
        processStateService: ProcessStateService,
        appFramework: AppFramework
    ) {
        Systrace.traceSynchronous("init-ndk-service") {
            processStateService.addListener(this)
            if (appFramework == AppFramework.UNITY) {
                unityCrashId = Uuid.getEmbUuid()
            }
            startNativeCrashMonitoring()
            cleanOldCrashFiles()
        }
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
                logger.logError(
                    String.format(
                        Locale.getDefault(),
                        "Failed to decode symbols from resources {resourceId=%d}.",
                        resourceId
                    ),
                    ex
                )
                logger.trackInternalError(InternalErrorType.INVALID_NATIVE_SYMBOLS, ex)
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
        val ndkDirs: List<File> = storageService.listFiles { file, name ->
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
        backgroundWorker.submit {
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
                    continue
                }
                errorFile.delete()
            }

            // delete map files that don't have matching crash files
            val mapFiles = getNativeMapFiles()
            for (mapFile in mapFiles) {
                if (hasNativeCrashFile(mapFile)) {
                    continue
                }
                mapFile.delete()
            }
        }
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
        val metadata = nativeCrash.metadata

        val nativeCrashEvent = Event(
            CRASH_REPORT_EVENT_NAME,
            null,
            Uuid.getEmbUuid(),
            nativeCrash.sessionId,
            EventType.CRASH,
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
        val nativeCrashNumber = preferencesService.incrementAndGetNativeCrashNumber()
        val nativeCrashMessageEvent = EventMessage(
            nativeCrashEvent,
            metadata?.deviceInfo,
            metadata?.appInfo,
            metadata?.userInfo,
            null,
            ApiClient.MESSAGE_VERSION,
            nativeCrash.getCrash(nativeCrashNumber)
        )
        try {
            deliveryService.sendCrash(nativeCrashMessageEvent, false)
        } catch (ex: Exception) {
            logger.logError(
                "Failed to report native crash to the api {sessionId=" + nativeCrash.sessionId +
                    ", crashId=" + nativeCrash.nativeCrashId,
                ex
            )
        }
    }

    private fun updateAppState(newAppState: String) {
        delegate._updateAppState(newAppState)
    }

    /**
     * Compute NDK metadata
     */
    private fun updateDeviceMetaData() {
        var newDeviceMetaData = getMetaData(true)
        if (newDeviceMetaData.length >= EMB_DEVICE_META_DATA_SIZE) {
            logger.logDebug("Removing session properties from metadata to avoid exceeding size limitation for NDK metadata.")
            newDeviceMetaData = getMetaData(false)
        }
        delegate._updateMetaData(newDeviceMetaData)
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

    companion object {
        /**
         * Signals to the API that the application was in the foreground.
         */
        private const val APPLICATION_STATE_FOREGROUND = "foreground"

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
        private const val MAX_NATIVE_CRASH_FILES_ALLOWED = 4
        private const val EMB_DEVICE_META_DATA_SIZE = 2048
        private const val HANDLER_CHECK_DELAY_MS = 5000
    }
}
