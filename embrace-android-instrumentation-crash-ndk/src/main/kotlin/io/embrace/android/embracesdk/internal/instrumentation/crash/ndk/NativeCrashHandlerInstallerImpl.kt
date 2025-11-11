package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.handler.MainThreadHandler
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.jni.JniDelegate
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.Worker
import java.io.File

private const val HANDLER_CHECK_DELAY_MS = 5000L

class NativeCrashHandlerInstallerImpl(
    private val sharedObjectLoader: SharedObjectLoader,
    private val delegate: JniDelegate,
    private val nativeInstallMessage: NativeInstallMessage,
    private val mainThreadHandler: MainThreadHandler,
    private val args: InstrumentationArgs,
    private val processIdProvider: Provider<String>,
    private val outputDir: Lazy<File>,
) : NativeCrashHandlerInstaller {

    private val backgroundWorker = args.backgroundWorker(Worker.Background.IoRegWorker)
    private val configService = args.configService
    private val logger = args.logger
    private val clock = args.clock

    override fun install() {
        backgroundWorker.submit {
            EmbTrace.trace("install-native-crash-signal-handlers") {
                startNativeCrashMonitoring()
            }
        }
    }

    private fun startNativeCrashMonitoring() {
        try {
            if (sharedObjectLoader.loadEmbraceNative()) {
                delegate.onSessionChange(sanitizeSessionId(args.sessionId()), createNativeReportPath())
                mainThreadHandler.postAtFrontOfQueue { installSignals() }
                mainThreadHandler.postDelayed(
                    Runnable(::checkSignalHandlersOverwritten),
                    HANDLER_CHECK_DELAY_MS
                )
                sessionIdTracker.addListener { sessionId ->
                    delegate.onSessionChange(sanitizeSessionId(sessionId), createNativeReportPath())
                }
            }
        } catch (ex: Exception) {
            logger.trackInternalError(InternalErrorType.NATIVE_HANDLER_INSTALL_FAIL, ex)
        }
    }

    private fun sanitizeSessionId(sid: String?) = sid ?: "null"

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
        EmbTrace.trace("native-install-handlers") {
            with(nativeInstallMessage) {
                delegate.installSignalHandlers(
                    markerFilePath,
                    appState.description,
                    reportId,
                    apiLevel,
                    is32bit,
                    devLogging
                )
            }
        }
    }

    private fun createNativeReportPath(): String {
        val metadata = StoredTelemetryMetadata(
            timestamp = clock.now(),
            uuid = sanitizeSessionId(args.sessionId()),
            processId = processIdProvider(),
            envelopeType = SupportedEnvelopeType.CRASH,
            payloadType = PayloadType.NATIVE_CRASH,
        )
        return File(outputDir.value, metadata.filename).absolutePath
    }
}
