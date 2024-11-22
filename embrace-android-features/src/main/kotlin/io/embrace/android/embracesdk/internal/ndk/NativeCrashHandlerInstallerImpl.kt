package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.handler.MainThreadHandler
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.ndk.jni.JniDelegate
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker

private const val HANDLER_CHECK_DELAY_MS = 5000L

internal class NativeCrashHandlerInstallerImpl(
    private val configService: ConfigService,
    private val sharedObjectLoader: SharedObjectLoader,
    private val logger: EmbLogger,
    private val repository: NdkServiceRepository,
    private val delegate: JniDelegate,
    private val backgroundWorker: BackgroundWorker,
    private val nativeInstallMessage: NativeInstallMessage,
    private val mainThreadHandler: MainThreadHandler,
) : NativeCrashHandlerInstaller {

    override fun install() {
        backgroundWorker.submit {
            Systrace.traceSynchronous("install-native-crash-signal-handlers") {
                startNativeCrashMonitoring()
            }
        }
    }

    private fun startNativeCrashMonitoring() {
        try {
            if (sharedObjectLoader.loadEmbraceNative()) {
                mainThreadHandler.postAtFrontOfQueue { installSignals() }
                mainThreadHandler.postDelayed(
                    Runnable(::checkSignalHandlersOverwritten),
                    HANDLER_CHECK_DELAY_MS
                )
            }
        } catch (ex: Exception) {
            logger.trackInternalError(InternalErrorType.NATIVE_HANDLER_INSTALL_FAIL, ex)
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

    private fun installSignals() {
        Systrace.traceSynchronous("native-install-handlers") {
            with(nativeInstallMessage) {
                delegate.installSignalHandlers(
                    reportPath,
                    markerFilePath,
                    sessionId,
                    appState,
                    reportId,
                    apiLevel,
                    is32bit,
                    devLogging
                )
            }
        }
        backgroundWorker.submit { repository.cleanOldCrashFiles() }
    }
}
