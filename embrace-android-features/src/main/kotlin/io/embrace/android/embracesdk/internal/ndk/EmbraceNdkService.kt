package io.embrace.android.embracesdk.internal.ndk

import android.os.Build
import android.os.Handler
import android.os.Looper
import io.embrace.android.embracesdk.internal.DeviceArchitecture
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.crash.CrashFileMarkerImpl
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.ndk.jni.JniDelegate
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateListener
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.internal.storage.StorageService
import io.embrace.android.embracesdk.internal.utils.Uuid

internal class EmbraceNdkService(
    private val storageService: StorageService,
    private val processStateService: ProcessStateService,
    private val configService: ConfigService,
    private val userService: UserService,
    private val sessionPropertiesService: SessionPropertiesService,
    private val sharedObjectLoader: SharedObjectLoader,
    private val logger: EmbLogger,
    private val delegate: JniDelegate,
    private val deviceArchitecture: DeviceArchitecture,
    private val handler: Handler = Handler(checkNotNull(Looper.getMainLooper())),
) : NdkService, ProcessStateListener {

    override fun initializeService(sessionIdTracker: SessionIdTracker) {
        Systrace.traceSynchronous("init-ndk-service") {
            if (startNativeCrashMonitoring { sessionIdTracker.getActiveSessionId() ?: "null" }) {
                processStateService.addListener(this)
                userService.addUserInfoListener(::onUserInfoUpdate)
                sessionIdTracker.addListener { updateSessionId(it ?: "") }
                sessionPropertiesService.addChangeListener(::onSessionPropertiesUpdate)
            }
        }
    }

    override fun updateSessionId(newSessionId: String) {
        if (sharedObjectLoader.loaded.get()) {
            delegate.updateSessionId(newSessionId)
        }
    }

    override fun onSessionPropertiesUpdate(properties: Map<String, String>) {
        // do nothing
    }

    override fun onUserInfoUpdate() {
        // do nothing
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

    private fun installSignals(sessionIdProvider: () -> String) {
        val reportBasePath = try {
            storageService.getOrCreateNativeCrashDir().absolutePath
        } catch (e: Exception) {
            logger.trackInternalError(InternalErrorType.NATIVE_HANDLER_INSTALL_FAIL, e)
            return
        }
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
    }

    private fun updateAppState(newAppState: String) {
        if (sharedObjectLoader.loaded.get()) {
            delegate.updateAppState(newAppState)
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
        private const val HANDLER_CHECK_DELAY_MS = 5000
    }
}
