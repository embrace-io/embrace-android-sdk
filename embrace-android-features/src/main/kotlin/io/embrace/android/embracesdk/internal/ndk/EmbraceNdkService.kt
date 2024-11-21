package io.embrace.android.embracesdk.internal.ndk

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.embrace.android.embracesdk.internal.DeviceArchitecture
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.crash.CrashFileMarkerImpl
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.ndk.jni.JniDelegate
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.payload.NativeCrashMetadata
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateListener
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.internal.storage.StorageService
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker

internal class EmbraceNdkService(
    context: Context,
    private val storageService: StorageService,
    private val metadataService: MetadataService,
    private val processStateService: ProcessStateService,
    private val configService: ConfigService,
    private val userService: UserService,
    private val sessionPropertiesService: SessionPropertiesService,
    private val sharedObjectLoader: SharedObjectLoader,
    private val logger: EmbLogger,
    private val repository: NdkServiceRepository,
    private val delegate: JniDelegate,
    private val backgroundWorker: BackgroundWorker,
    private val deviceArchitecture: DeviceArchitecture,
    private val serializer: PlatformSerializer,
    private val handler: Handler = Handler(checkNotNull(Looper.getMainLooper())),
) : NdkService, ProcessStateListener {

    private val processor: NativeCrashProcessorImpl = NativeCrashProcessorImpl(
        context,
        sharedObjectLoader,
        logger,
        repository,
        delegate,
        deviceArchitecture,
        serializer
    )

    override val symbolsForCurrentArch
        get() = processor.symbolsForCurrentArch

    override fun initializeService(sessionIdTracker: SessionIdTracker) {
        Systrace.traceSynchronous("init-ndk-service") {
            if (startNativeCrashMonitoring { sessionIdTracker.getActiveSessionId() ?: "null" }) {
                processStateService.addListener(this)
                userService.addUserInfoListener(::onUserInfoUpdate)
                sessionIdTracker.addListener { updateSessionId(it ?: "") }
                sessionPropertiesService.addChangeListener(::onSessionPropertiesUpdate)
                repository.cleanOldCrashFiles()
            }
        }
    }

    override fun updateSessionId(newSessionId: String) {
        if (sharedObjectLoader.loaded.get()) {
            delegate.updateSessionId(newSessionId)
        }
    }

    override fun onSessionPropertiesUpdate(properties: Map<String, String>) {
        backgroundWorker.submit {
            updateDeviceMetaData()
        }
    }

    override fun onUserInfoUpdate() {
        backgroundWorker.submit {
            updateDeviceMetaData()
        }
    }

    override fun onBackground(timestamp: Long) {
        updateAppState(APPLICATION_STATE_BACKGROUND)
    }

    override fun onForeground(coldStart: Boolean, timestamp: Long) {
        updateAppState(APPLICATION_STATE_FOREGROUND)
    }

    private fun startNativeCrashMonitoring(sessionIdProvider: () -> String): Boolean {
        return try {
            if (sharedObjectLoader.loadEmbraceNative()) {
                createCrashReportDirectory()
                handler.postAtFrontOfQueue { installSignals(sessionIdProvider) }
                handler.postDelayed(
                    Runnable(::checkSignalHandlersOverwritten),
                    HANDLER_CHECK_DELAY_MS.toLong()
                )
                true
            } else {
                false
            }
        } catch (ex: Exception) {
            logger.trackInternalError(InternalErrorType.NATIVE_HANDLER_INSTALL_FAIL, ex)
            false
        }
    }

    private fun checkSignalHandlersOverwritten() {
        if (configService.autoDataCaptureBehavior.is3rdPartySigHandlerDetectionEnabled()) {
            val culprit = delegate.checkForOverwrittenHandlers()
            if (culprit != null) {
                if (shouldIgnoreOverriddenHandler(culprit)) {
                    return
                }
                delegate.reinstallSignalHandlers()
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
        directoryFile.mkdirs()
    }

    private fun installSignals(sessionIdProvider: () -> String) {
        val reportBasePath = storageService.getNativeCrashDir().absolutePath
        val markerFilePath = storageService.getFileForWrite(
            CrashFileMarkerImpl.CRASH_MARKER_FILE_NAME
        ).absolutePath

        val nativeCrashId: String = Uuid.getEmbUuid()
        val is32bit = deviceArchitecture.is32BitDevice
        Systrace.traceSynchronous("native-install-handlers") {
            delegate.installSignalHandlers(
                reportBasePath,
                markerFilePath,
                sessionIdProvider(),
                processStateService.getAppState(),
                nativeCrashId,
                Build.VERSION.SDK_INT,
                is32bit,
                false
            )
        }
        backgroundWorker.submit(runnable = ::updateDeviceMetaData)
    }

    override fun getLatestNativeCrash(): NativeCrashData? = processor.getLatestNativeCrash()

    override fun getNativeCrashes(): List<NativeCrashData> = processor.getNativeCrashes()

    override fun deleteAllNativeCrashes() = processor.deleteAllNativeCrashes()

    private fun updateAppState(newAppState: String) {
        if (sharedObjectLoader.loaded.get()) {
            delegate.updateAppState(newAppState)
        }
    }

    /**
     * Compute NDK metadata
     */
    private fun updateDeviceMetaData() {
        if (sharedObjectLoader.loaded.get()) {
            val src = captureMetaData(true)
            var json = serializeMetadata(src)
            if (json.length >= EMB_DEVICE_META_DATA_SIZE) {
                json = serializeMetadata(src.copy(sessionProperties = null))
            }
            delegate.updateMetaData(json)
        }
    }

    private fun captureMetaData(includeSessionProperties: Boolean): NativeCrashMetadata {
        return Systrace.trace("gather-native-metadata") {
            NativeCrashMetadata(
                metadataService.getAppInfo(),
                metadataService.getDeviceInfo(),
                userService.getUserInfo(),
                if (includeSessionProperties) sessionPropertiesService.getProperties() else null
            )
        }
    }

    private fun serializeMetadata(newDeviceMetaData: NativeCrashMetadata): String {
        return Systrace.trace("serialize-native-metadata") {
            serializer.toJson(newDeviceMetaData)
        }
    }

    internal companion object {
        /**
         * Signals to the API that the application was in the foreground.
         */
        private const val APPLICATION_STATE_FOREGROUND = "foreground"

        /**
         * Signals to the API that the application was in the background.
         */
        private const val APPLICATION_STATE_BACKGROUND = "background"

        internal const val NATIVE_CRASH_FILE_PREFIX = "emb_ndk"
        internal const val NATIVE_CRASH_FILE_SUFFIX = ".crash"
        internal const val NATIVE_CRASH_ERROR_FILE_SUFFIX = ".error"
        internal const val NATIVE_CRASH_MAP_FILE_SUFFIX = ".map"
        private const val EMB_DEVICE_META_DATA_SIZE = 2048
        private const val HANDLER_CHECK_DELAY_MS = 5000
    }
}
