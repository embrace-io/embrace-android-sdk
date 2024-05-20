package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.EventType
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.UnityInternalInterface
import io.embrace.android.embracesdk.capture.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod

internal class UnityInternalInterfaceImpl(
    private val embrace: EmbraceImpl,
    private val impl: EmbraceInternalInterface,
    private val hostedSdkVersionInfo: HostedSdkVersionInfo,
    private val logger: EmbLogger
) : EmbraceInternalInterface by impl, UnityInternalInterface {

    override fun setUnityMetaData(
        unityVersion: String?,
        buildGuid: String?,
        unitySdkVersion: String?
    ) {
        if (embrace.isStarted()) {
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
        exceptionType: LogExceptionType
    ) {
        if (embrace.isStarted()) {
            embrace.logMessage(
                EventType.ERROR_LOG,
                "Unity exception",
                null,
                null,
                stacktrace,
                exceptionType,
                null,
                null,
                name,
                message
            )
        } else {
            logger.logSdkNotInitialized("log Unity exception")
        }
    }

    override fun recordIncompleteNetworkRequest(
        url: String,
        httpMethod: String,
        startTime: Long,
        endTime: Long,
        errorType: String?,
        errorMessage: String?,
        traceId: String?
    ) {
        embrace.recordNetworkRequest(
            EmbraceNetworkRequest.fromIncompleteRequest(
                url,
                HttpMethod.fromString(httpMethod),
                startTime,
                endTime,
                errorType ?: "",
                errorMessage ?: "",
                traceId,
                null,
                null
            )
        )
    }

    override fun recordCompletedNetworkRequest(
        url: String,
        httpMethod: String,
        startTime: Long,
        endTime: Long,
        bytesSent: Long,
        bytesReceived: Long,
        statusCode: Int,
        traceId: String?
    ) {
        embrace.recordNetworkRequest(
            EmbraceNetworkRequest.fromCompletedRequest(
                url,
                HttpMethod.fromString(httpMethod),
                startTime,
                endTime,
                bytesSent,
                bytesReceived,
                statusCode,
                traceId,
                null,
                null
            )
        )
    }

    override fun installUnityThreadSampler() {
        embrace.installUnityThreadSampler()
    }
}
