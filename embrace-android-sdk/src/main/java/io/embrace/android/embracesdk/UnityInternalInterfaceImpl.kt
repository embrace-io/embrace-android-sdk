package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.prefs.PreferencesService

internal class UnityInternalInterfaceImpl(
    private val embrace: EmbraceImpl,
    private val impl: EmbraceInternalInterface,
    private val preferencesService: PreferencesService,
    private val metadataService: MetadataService,
    private val logger: InternalEmbraceLogger
) : EmbraceInternalInterface by impl, UnityInternalInterface {

    override fun setUnityMetaData(
        unityVersion: String?,
        buildGuid: String?,
        unitySdkVersion: String?
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
            val unityVersionNumber = preferencesService.unityVersionNumber
            if (unityVersionNumber != null) {
                logger.logDeveloper("Embrace", "Unity version number is present")
                if (unityVersion != unityVersionNumber) {
                    logger.logDeveloper(
                        "Embrace",
                        "Setting a new Unity version number"
                    )
                    metadataService.setUnityVersionNumber(unityVersion)
                }
            } else {
                logger.logDeveloper("Embrace", "Setting Unity version number")
                metadataService.setUnityVersionNumber(unityVersion)
            }
            val unityBuildIdNumber = preferencesService.unityBuildIdNumber
            if (unityBuildIdNumber != null) {
                logger.logDeveloper("Embrace", "Unity build id is present")
                if (buildGuid != unityBuildIdNumber) {
                    logger.logDeveloper("Embrace", "Setting a Unity new build id")
                    metadataService.setUnityBuildIdNumber(buildGuid)
                }
            } else {
                logger.logDeveloper("Embrace", "Setting Unity build id")
                metadataService.setUnityBuildIdNumber(buildGuid)
            }
            if (unitySdkVersion == null) {
                logger.logDeveloper("Embrace", "Unity SDK version is null.")
                return
            }
            val unitySdkVersionNumber = preferencesService.unitySdkVersionNumber
            if (unitySdkVersionNumber != null) {
                logger.logDeveloper("Embrace", "Unity SDK version number is present")
                if (unitySdkVersion != unitySdkVersionNumber) {
                    logger.logDeveloper(
                        "Embrace",
                        "Setting a new Unity SDK version number"
                    )
                    metadataService.setUnitySdkVersionNumber(unitySdkVersion)
                }
            } else {
                logger.logDeveloper("Embrace", "Setting Unity SDK version number")
                metadataService.setUnitySdkVersionNumber(unitySdkVersion)
            }
        } else {
            logger.logSDKNotInitialized("set Unity metadata")
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
        if (embrace.isStarted) {
            logger.logError("message: $message -- stacktrace: $stacktrace")
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
            logger.logSDKNotInitialized("log Unity exception")
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
