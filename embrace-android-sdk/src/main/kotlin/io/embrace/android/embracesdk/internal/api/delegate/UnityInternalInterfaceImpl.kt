package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.UnityInternalInterface
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.logs.LogExceptionType

internal class UnityInternalInterfaceImpl(
    private val embrace: EmbraceImpl,
    private val impl: EmbraceInternalInterface,
    private val hostedSdkVersionInfo: HostedSdkVersionInfo,
    private val logger: InternalLogger,
) : EmbraceInternalInterface by impl, UnityInternalInterface {

    override fun setUnityMetaData(
        unityVersion: String?,
        buildGuid: String?,
        unitySdkVersion: String?,
    ) {
        if (embrace.isStarted) {
            if (unityVersion == null || buildGuid == null) {
                val sdkVersionMessage = unitySdkVersion ?: "null or previous than 1.7.5"
                logger.logError(
                    "Unity metadata is corrupted or malformed. Unity version is " +
                        unityVersion + ", Unity build id is " + buildGuid +
                        " and Unity SDK version is " + sdkVersionMessage
                )
                return
            }
            if (unitySdkVersion != null) {
                hostedSdkVersionInfo.hostedPlatformVersion = unityVersion
                hostedSdkVersionInfo.hostedSdkVersion = unitySdkVersion
                hostedSdkVersionInfo.unityBuildIdNumber = buildGuid
            }
        } else {
            logger.logSdkNotInitialized("set Unity metadata")
        }
    }

    override fun logUnhandledUnityException(name: String, message: String, stacktrace: String?) {
        logUnityException(name, message, stacktrace, LogExceptionType.UNHANDLED)
    }

    override fun logHandledUnityException(name: String, message: String, stacktrace: String?) {
        logUnityException(name, message, stacktrace, LogExceptionType.HANDLED)
    }

    private fun logUnityException(
        name: String?,
        message: String,
        stacktrace: String?,
        exceptionType: LogExceptionType,
    ) {
        if (embrace.isStarted) {
            val attrs = mutableMapOf(
                "emb.type" to "sys.exception",
                "emb.private.send_mode" to "immediate",
                "exception.message" to message,
            )

            // add exception name + message attrs
            name?.let { attrs["exception.type"] = it }
            stacktrace?.let { attrs["exception.stacktrace"] = it }

            if (exceptionType != LogExceptionType.NONE) {
                attrs["emb.exception_handling"] = exceptionType.value
            }

            embrace.logMessage(
                severity = Severity.ERROR,
                message = "Unity exception",
                properties = attrs,
            )
        } else {
            logger.logSdkNotInitialized("log Unity exception")
        }
    }
}
